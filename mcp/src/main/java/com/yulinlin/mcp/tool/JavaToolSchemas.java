package com.yulinlin.mcp.tool;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.math.*;
import java.util.*;

/** Resolves Jackson wire properties, including generic DTOs, once at startup. */
final class JavaToolSchemas {
    private final ObjectMapper json;
    private final ObjectNode definitions;
    private final Map<JavaType,String> names=new LinkedHashMap<>();
    private boolean reading;

    JavaToolSchemas(ObjectMapper json){this.json=json;this.definitions=json.createObjectNode();}

    ObjectNode input(JavaType type) {
        reading=true;
        if(type==null)return object("无参数");
        if(!dto(type))throw unsupported(type,"请求参数必须为一个 DTO");
        resolve(type);
        ObjectNode result=definitions.get(names.get(type)).deepCopy();
        result.set("$defs",definitions);
        return result;
    }
    ObjectNode output(JavaType type) {
        reading=false;
        ObjectNode result=object("工具成功结果；业务返回值位于 data，失败时检查 isError 和 content。");
        ObjectNode data=resolve(type);
        if(!type.isPrimitive())data=nullable(data);
        result.withObject("/properties").set("data",data);
        result.withObject("/properties").set("warning",scalar("string","可选完成警告：操作可能已成功，请勿自动重试写操作。"));
        result.withArray("required").add("data");
        if(!definitions.isEmpty())result.set("$defs",definitions);
        return result;
    }
    private ObjectNode resolve(JavaType type) {
        Class<?> raw=type.getRawClass();
        if(raw==Object.class||JsonNode.class.isAssignableFrom(raw))throw unsupported(type,"不能推断动态 Object/JsonNode，请使用显式 McpToolProvider");
        if(type.isMapLikeType()) {
            if(type.getKeyType()==null||type.getKeyType().getRawClass()!=String.class||type.getContentType()==null)
                throw unsupported(type,"Map 必须声明 String 键和明确的值类型");
            var result=scalar("object","字符串键到声明值类型的映射。");
            result.set("additionalProperties",nullable(resolve(type.getContentType())));return result;
        }
        if(raw==void.class||raw==Void.class)return scalar("null","无返回值。");
        if(raw==String.class||raw==char.class||raw==Character.class||raw==UUID.class)return scalar("string",raw==UUID.class?"UUID 字符串。":"文本。");
        if(raw==boolean.class||raw==Boolean.class)return scalar("boolean","布尔值。");
        if(raw==byte.class||raw==Byte.class||raw==short.class||raw==Short.class||raw==int.class||raw==Integer.class||raw==long.class||raw==Long.class||raw==BigInteger.class)return scalar("integer","整数。");
        if(raw==double.class||raw==Double.class||raw==float.class||raw==Float.class||raw==BigDecimal.class)return scalar("number","数值。");
        if(raw.isEnum()) {
            var result=json.createObjectNode();var values=result.putArray("enum");
            for(Object constant:raw.getEnumConstants()) {
                JsonNode value=json.valueToTree(constant);
                if(!value.isTextual())throw unsupported(type,"非字符串枚举序列化请使用显式 Schema");
                values.add(value);
            }
            result.put("type","string");result.put("description",raw.getSimpleName()+" 枚举。");apply(result,raw.getAnnotation(Schema.class));return result;
        }
        if(type.isArrayType()||type.isCollectionLikeType()) {
            if(raw==byte[].class)throw unsupported(type,"byte[] 的 Base64 表示请使用 String 或显式 Schema");
            JavaType item=type.getContentType();if(item==null)throw unsupported(type,"集合需要明确元素类型");
            var result=scalar("array","元素列表。");var element=resolve(item);result.set("items",item.isPrimitive()?element:nullable(element));return result;
        }
        if(!dto(type))throw unsupported(type,"该类型的 JSON 表示不明确，请使用 DTO、基础类型或显式 Schema");
        if(names.containsKey(type))return json.createObjectNode().put("$ref","#/$defs/"+names.get(type));
        if(names.size()>=128)throw unsupported(type,"DTO 类型图超过 128 个类型");
        String name="T"+(names.size()+1);names.put(type,name);
        ObjectNode definition=object(raw.getSimpleName());definitions.set(name,definition);
        apply(definition,raw.getAnnotation(Schema.class));
        BeanDescription bean=reading?json.getDeserializationConfig().introspect(type):json.getSerializationConfig().introspect(type);
        if(bean.findAnyGetter()!=null||bean.findAnySetterAccessor()!=null||bean.findJsonValueAccessor()!=null||bean.getClassInfo().hasAnnotation(JsonSerialize.class)||bean.getClassInfo().hasAnnotation(JsonDeserialize.class)||bean.getClassInfo().hasAnnotation(JsonTypeInfo.class)||reshaped(bean.getClassInfo().getAnnotation(JsonFormat.class)))
            throw unsupported(type,"动态属性、自定义序列化或多态 DTO 请使用显式 Schema");
        if(raw.isInterface()||java.lang.reflect.Modifier.isAbstract(raw.getModifiers()))throw unsupported(type,"抽象 DTO 无法确定具体结构");
        ObjectNode properties=definition.withObject("/properties");ArrayNode required=definition.withArray("required");
        for(BeanPropertyDefinition property:bean.findProperties()) {
            if(reading?!property.couldDeserialize():!property.couldSerialize())continue;
            var member=reading?property.getPrimaryMember():property.getAccessor();if(member==null)continue;
            if(annotation(property,JsonUnwrapped.class)!=null||annotation(property,JsonSerialize.class)!=null||annotation(property,JsonDeserialize.class)!=null||annotation(property,JsonTypeInfo.class)!=null||reshaped(annotation(property,JsonFormat.class)))
                throw unsupported(type,"字段 "+property.getName()+" 使用了自定义 JSON 结构，请提供显式 Schema");
            JavaType fieldType=property.getPrimaryType();Schema schema=annotation(property,Schema.class);
            ObjectNode field=resolve(fieldType);apply(field,schema);
            boolean nonnull=annotation(property,NotNull.class)!=null||annotation(property,NotBlank.class)!=null||annotation(property,NotEmpty.class)!=null;
            boolean isRequired=nonnull||property.isRequired()||(schema!=null&&(schema.required()||schema.requiredMode()==Schema.RequiredMode.REQUIRED));
            constraints(field,property,fieldType);
            if(!fieldType.isPrimitive()&&!nonnull)field=nullable(field);
            if(!field.has("description"))field.put("description",property.getName());
            properties.set(property.getName(),field);
            if(isRequired)required.add(property.getName());
        }
        if(properties.isEmpty())throw unsupported(type,"DTO 没有可映射字段");
        return json.createObjectNode().put("$ref","#/$defs/"+name);
    }
    private boolean dto(JavaType type) {
        Class<?> raw=type.getRawClass();
        return !raw.isPrimitive()&&!raw.isEnum()&&!type.isContainerType()&&!raw.getName().startsWith("java.")
            &&!raw.getName().startsWith("jakarta.")&&!raw.getName().startsWith("org.springframework.")
            &&!JsonNode.class.isAssignableFrom(raw)&&raw!=Object.class;
    }
    private ObjectNode object(String description) {
        var node=scalar("object",description);node.putObject("properties");node.putArray("required");node.put("additionalProperties",false);return node;
    }
    private ObjectNode scalar(String type,String description){return json.createObjectNode().put("type",type).put("description",description);}
    private ObjectNode nullable(ObjectNode value) {
        if("null".equals(value.path("type").asText()))return value;
        var wrapper=json.createObjectNode();wrapper.putArray("anyOf").add(value).add(json.createObjectNode().put("type","null"));
        for(String key:List.of("description","title","default","examples","deprecated"))if(value.has(key))wrapper.set(key,value.get(key));
        return wrapper;
    }
    private boolean reshaped(JsonFormat format){return format!=null&&format.shape()!=JsonFormat.Shape.ANY&&format.shape()!=JsonFormat.Shape.NATURAL;}
    private <A extends Annotation>A annotation(BeanPropertyDefinition property,Class<A> type) {
        for(AnnotatedMember member:new AnnotatedMember[]{property.getPrimaryMember(),property.getField(),property.getGetter(),property.getSetter(),property.getConstructorParameter()}) {
            if(member!=null){A value=member.getAnnotation(type);if(value!=null){
                try {
                    var groups=value.annotationType().getMethod("groups");
                    Class<?>[] selected=(Class<?>[])groups.invoke(value);
                    if(selected.length>0&&Arrays.stream(selected).noneMatch(g->g==jakarta.validation.groups.Default.class))continue;
                }catch(NoSuchMethodException ignored){}catch(ReflectiveOperationException e){throw new IllegalArgumentException(e);}
                return value;
            }}
        }
        return null;
    }
    private void constraints(ObjectNode field,BeanPropertyDefinition p,JavaType type) {
        boolean array=type.isArrayType()||type.isCollectionLikeType();String min=type.isMapLikeType()?"minProperties":array?"minItems":"minLength",max=type.isMapLikeType()?"maxProperties":array?"maxItems":"maxLength";
        Size size=annotation(p,Size.class);if(size!=null){field.put(min,size.min());if(size.max()!=Integer.MAX_VALUE)field.put(max,size.max());}
        if(annotation(p,NotBlank.class)!=null||annotation(p,NotEmpty.class)!=null)field.put(min,Math.max(1,field.path(min).asInt()));
        // Java regex semantics/flags are checked by Bean Validation, not translated to a different dialect.
        Min lo=annotation(p,Min.class);if(lo!=null)field.put("minimum",lo.value());
        Max hi=annotation(p,Max.class);if(hi!=null)field.put("maximum",hi.value());
        DecimalMin dlo=annotation(p,DecimalMin.class);if(dlo!=null)field.put(dlo.inclusive()?"minimum":"exclusiveMinimum",new BigDecimal(dlo.value()));
        DecimalMax dhi=annotation(p,DecimalMax.class);if(dhi!=null)field.put(dhi.inclusive()?"maximum":"exclusiveMaximum",new BigDecimal(dhi.value()));
        if(annotation(p,Positive.class)!=null)field.put("exclusiveMinimum",0);
        if(annotation(p,PositiveOrZero.class)!=null)field.put("minimum",0);
        if(annotation(p,Negative.class)!=null)field.put("exclusiveMaximum",0);
        if(annotation(p,NegativeOrZero.class)!=null)field.put("maximum",0);
        if(annotation(p,AssertTrue.class)!=null)field.put("const",true);
        if(annotation(p,AssertFalse.class)!=null)field.put("const",false);
        if(annotation(p,Email.class)!=null)field.put("format","email");
    }
    private void apply(ObjectNode node,Schema schema) {
        if(schema==null)return;
        // Fail visibly instead of silently publishing an inaccurate contract for advanced OpenAPI features.
        Set<String> supported=Set.of("description","title","example","examples","defaultValue","allowableValues","required","requiredMode","nullable","minimum","maximum","exclusiveMinimum","exclusiveMaximum","minLength","maxLength","pattern","format","deprecated");
        for(var method:Schema.class.getDeclaredMethods()) {
            if(supported.contains(method.getName()))continue;
            try {if(!Objects.deepEquals(method.invoke(schema),method.getDefaultValue()))throw new IllegalArgumentException("自动 MCP Schema 暂不支持 @Schema."+method.getName()+"，请使用显式 McpToolProvider");}
            catch(ReflectiveOperationException e){throw new IllegalArgumentException("读取 @Schema 失败",e);}
        }
        if(!schema.description().isBlank())node.put("description",schema.description());
        if(!schema.title().isBlank())node.put("title",schema.title());
        if(!schema.format().isBlank())node.put("format",schema.format());
        if(!schema.pattern().isEmpty())node.put("pattern",schema.pattern());
        if(schema.minLength()>0)node.put("minLength",schema.minLength());
        if(schema.maxLength()!=Integer.MAX_VALUE)node.put("maxLength",schema.maxLength());
        if(!schema.minimum().isEmpty())node.put(schema.exclusiveMinimum()?"exclusiveMinimum":"minimum",new BigDecimal(schema.minimum()));
        if(!schema.maximum().isEmpty())node.put(schema.exclusiveMaximum()?"exclusiveMaximum":"maximum",new BigDecimal(schema.maximum()));
        if(schema.deprecated())node.put("deprecated",true);
        if(schema.allowableValues().length>0){var values=node.putArray("enum");for(String v:schema.allowableValues())values.add(literal(node,v));}
        if(!schema.defaultValue().isEmpty())node.set("default",literal(node,schema.defaultValue()));
        if(!schema.example().isEmpty()||schema.examples().length>0){var examples=node.putArray("examples");if(!schema.example().isEmpty())examples.add(literal(node,schema.example()));for(String v:schema.examples())examples.add(literal(node,v));}
    }
    private JsonNode literal(ObjectNode schema,String text) {
        if("string".equals(schema.path("type").asText()))return TextNode.valueOf(text);
        try{return json.readTree(text);}catch(Exception e){throw new IllegalArgumentException("@Schema 示例/默认值必须符合字段 JSON 类型",e);}
    }
    private IllegalArgumentException unsupported(JavaType type,String reason){return new IllegalArgumentException(reason+": "+type.toCanonical());}
}
