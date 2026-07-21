package com.yulinlin.generate.service.impl;


import com.yulinlin.common.model.ModelSelectWrapper;

import com.yulinlin.common.service.impl2.ServiceImpl;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.util.StringUtil;
import com.yulinlin.generate.pojo.po.GeneratePo;
import com.yulinlin.generate.pojo.vo.ColumnVo;
import com.yulinlin.generate.pojo.vo.TableVo;
import com.yulinlin.generate.util.TemplateUtil;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yulinlin.generate.pojo.entity.TemplateEntity;
import com.yulinlin.generate.service.ITemplateService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Service
@Transactional
public class TemplateServiceImpl extends ServiceImpl<TemplateEntity> implements ITemplateService {

    @SneakyThrows
    private String genCode(TemplateEntity view, TableVo table,
                           List<ColumnVo> columnList,
                           GeneratePo po){
        String className =table.getClassName();
        HashMap map =  new HashMap<>();

        map.put("fileType",view.getFileType());

        map.put("prefix",view.getPrefix());
        map.put("suffix",view.getSuffix());

        map.put("tableComment",table.getTableComment());

        map.put("tableName",table.getTableName());


        map.put("className",className);


        map.put("columnList",columnList);

        map.put("moduleName",view.getModuleName());
        map.put("domain",po.getDomain());
        String packageSpace = po.getPackageSpace();

        if (StringUtil.isNotNull(po.getDomain())) {
            packageSpace+="."+po.getDomain();
        }

        map.put("packageSpace",packageSpace);


        String html = TemplateUtil.renderString(view.getContent(),map);


        return html;



    }

    @Transactional
    @SneakyThrows
    public void generateCodeFile(GeneratePo po, File root) {
        List<TemplateEntity> templates =  findByIdIn(po.getTemplateIds());


        SessionUtil.route().pushSession(po.getComputeId());



        try {


            List<TableVo> tableList =  ModelSelectWrapper.newInstance(TableVo.class)
                    .eq("tableSchema",po.getTableSchema())
                    .in("tableName",po.getTableNames())
                    .selectList();




            for (TableVo tableVo : tableList) {
            List<ColumnVo> columnList =  tableVo.findColumnList();

            for (TemplateEntity view : templates) {
                String html =  genCode(view,tableVo,columnList,po);

                String className =view.getPrefix()+tableVo.getClassName()+view.getSuffix();
                String path = root.getPath();




                if(StringUtil.isNotNull( po.getPackageSpace())) {
                    path += "/"+po.getPackageSpace();
                }

                if(StringUtil.isNotNull( po.getDomain())) {
                    path += "/"+po.getDomain();
                }
                path += "/"+view.getModuleName()+"/"+className;


                path = path
                        .replace("\\","/")
                        .replace(".","/");

                path+="."+view.getFileType();


                File f  =new File(path);


                f.getParentFile().mkdirs();
                byte[] bytes = html.getBytes();



                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(bytes);
                    System.out.println("写入成功！");
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }



        }
        }finally {
            SessionUtil.route().popSession();
        }
    }
}
