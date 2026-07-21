package com.yulinlin.common.util;


import com.yulinlin.common.domain.ITreeNode;
import com.yulinlin.common.domain.enums.EnumItem;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.lazy.SmartCache;
import io.swagger.annotations.ApiModelProperty;

import java.util.*;
import java.util.stream.Collectors;

@JoinTable("sys_enum")

public class EnumNode  implements ITreeNode<EnumNode> {

    @ApiModelProperty("父id")
    private String parentId;
@JoinMeta(primaryKey = true)
@JoinField
    @ApiModelProperty("编码")
    private String id;

    @ApiModelProperty("编码")
    private String code;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("值")
    private String value;


    @ApiModelProperty("枚举类型")
    private String enumType;



    @JoinField(exist = false)
    @ApiModelProperty("孩子数组")
    private List<EnumNode> children = new ArrayList<>();

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setEnumType(String enumType) {
        this.enumType = enumType;
    }

    public void setChildren(List<EnumNode> children) {
        this.children = children;
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getValue() {
        return value;
    }

    public String getEnumType() {
        return enumType;
    }

    public List<EnumNode> getChildren() {
        return children;
    }

    @Override
    public void addChildren(EnumNode node) {
        if(children == null){
            children = new ArrayList<>();
        }
        children.add(node);
    }

    public List<EnumNode> list(){
        return children;
    }

    public EnumNode list(String code){
        for (EnumNode child : children) {
           if(child.getCode().equals(code)){
               return child;
           }
        }

        return null;
    }

    public Map<String,EnumNode> map(){
        Map map =  new HashMap<>();
        for (EnumNode child : children) {
            map.put(child.code,child);
        }
        return map;
    }


    public String string(){
        return value;
    }


    public List<String> stringSplit(String split){
        return Arrays.asList( value.split(split))
                .stream().filter(row -> row.length() > 0)
                .collect(Collectors.toList());

    }

    public List<EnumItem> stringSplitToEnum(String split){
        List<String> data = stringSplit(split);
        ArrayList<EnumItem> objects = new ArrayList<>();

        for (String string : data) {
            String[] kv = string.split("=");
            if(kv.length == 1){
                objects.add(new EnumItem(string,string));
            }else {
                objects.add(new EnumItem(kv[0],kv[1]));
            }
        }
        return objects;

    }

    public List<EnumItem> stringSplitToEnum(){
        return stringSplitToEnum(",");
    }

    public List<String> stringSplit(){
        return stringSplit(",");
    }


    public Integer parseInt(){
        return Integer.parseInt(value);
    }

    public Double parseDouble(){
        if(value.endsWith("%")){
           return Double.parseDouble( value.substring(0,value.length()-1)) / 100;
        }
        return Double.parseDouble(value);
    }
    public Float parseFloat(){
        return Float.parseFloat(value);
    }
    public Boolean parseBoolean(){
        return value.equals("true") || value.equals("1");
    }


   static SmartCache< List<EnumNode>> supplier = SmartCache.of(() -> {
        return ModelSelectWrapper.newInstance(EnumNode.class)
                .selectTree();
    },15,false);

    public static EnumNode newInstance(String code){

        List<EnumNode> nodes =   supplier.get();

        for (EnumNode enumItem : nodes) {
            if(enumItem.getCode().equals(code)){
                return enumItem;
            }
        }

        return null;
    }

}
