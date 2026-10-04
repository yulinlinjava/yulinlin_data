package com.yulinlin.admin;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;


import java.util.*;

public class JsoupUtil {

    private static Map<String,String> symbols;

    static {
        symbols = new HashMap<>();
        /**
         * < 替换为 &lt;
         * > 替换为 &gt;
         * & 替换为 &amp;
         * " 替换为 &quot;
         * ' 替换为 &apos;
         */
        symbols.put("<","&lt;");
        symbols.put(">","&gt;");
        symbols.put("&","&amp;");
        symbols.put("\"","&quot;");
        symbols.put("'","&apos;");
    }

    private static String strEncode(String data) {
            //如果是字符串，替换
            for (Map.Entry<String, String> entry : symbols.entrySet()) {
                data =  data.replaceAll(entry.getKey(),entry.getValue());
            }
            return data;
    }



    private static void dfs(Element element){

        for (Node node : element.childNodes()) {
            if(node.getClass().equals(TextNode.class)){
                TextNode textNode =(TextNode) node;
                String text = textNode.text();
                text = strEncode(text);
                textNode.text(text);
            }
        }

            for (Element ele : element.children()) {
                dfs(ele);
            }

    }

    public static String encode(String xml) {
        Document parse = Jsoup.parse(xml, Parser.xmlParser());

        dfs(parse);
        return parse.html();


    }
}
