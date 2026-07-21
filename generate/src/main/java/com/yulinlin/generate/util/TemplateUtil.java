package com.yulinlin.generate.util;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;

import java.io.StringWriter;

public class TemplateUtil {


  static Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);

  static {
    StringTemplateLoader templateLoader = new StringTemplateLoader();
    configuration.setTemplateLoader(templateLoader);
    configuration.setDefaultEncoding("UTF-8");
  }

  /**
   * @param text 字符串模板
   * @param params 参数
   * @return
   */

  @SneakyThrows
  public static String renderString(String text, Object params) {
    StringWriter stringWriter = new StringWriter();
    Template template =
        new Template("xx", text, configuration);
    template.process(params, stringWriter);
    return stringWriter.toString();
  }



}
