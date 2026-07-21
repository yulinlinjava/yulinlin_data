package com.yulinlin.starter.util;


import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;

public class ResponseUtil {


	public static HttpServletResponse getResponse() {
		HttpServletResponse response = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getResponse();

		return response;
	}




	public static void setHeader(String key,String val){
		HttpServletResponse response = getResponse();
		response.setHeader(key,val);
	}



	public static void sendRedirect(String url) throws Exception  {
		HttpServletResponse response = getResponse();
		response.sendRedirect(url);
	}






	public static void downloadFile(InputStream outputStream,String fileName) throws Exception {
		HttpServletResponse response = getResponse();
		response.setHeader("Content-Length", outputStream.available()+"");
		// ContentType，即告诉客户端所发送的数据属于什么类型
		response.setContentType("application/octet-stream; charset=UTF-8");

		// 获得文件的长度

		// 设置编码格式
		response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(fileName, "UTF-8"));


		InputStream myStream =outputStream;
		OutputStream writer = response.getOutputStream();

		byte[] bs =  new byte[2048];
		int i = 0;
		while (i != -1){
			i = myStream.read(bs);
			writer.write(bs);
			writer.flush();
		}


		writer.close();

	}


  public static void downloadFile(File f)throws Exception {
		try(		InputStream myStream = new FileInputStream(f);) {
			downloadFile(myStream,f.getName());
		}


  }



	 public static void writeCookie(String cookieName,String value){
		 HttpServletResponse response = getResponse();
		 Cookie cookie = new Cookie(cookieName,value);
	        cookie.setPath("/");
	        cookie.setMaxAge(1*24*60*60);
	        response.addCookie(cookie);
	    }

	public static void write(String contentType,String obj) {
		HttpServletResponse response = getResponse();
		PrintWriter out = null;
		try {
			String json;
			response.setCharacterEncoding("UTF-8");
			response.setContentType(contentType);
			out = response.getWriter();
			out.write(obj);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(out != null) {
				out.close();
			}

		}
	}

	public static void writeJson(String obj) {
		write("text/json;charset=utf-8",obj);


	}

	public static void writeScript(String obj) {
		write("application/javascript;charset=utf-8",obj);

	}

	public static void writeHtml(String obj) {
		write("text/html;charset=utf-8",obj);

	}

	 /**
	  * 输出图片
	  * @param image
	 * @throws IOException 
	  */
	public static void writeImage(BufferedImage  image) {
		HttpServletResponse response = getResponse();
		response.setDateHeader("Expires", 0);
		        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");  
		        response.addHeader("Cache-Control", "post-check=0, pre-check=0");  
		        response.setHeader("Pragma", "no-cache");  
		        response.setContentType("image/jpeg");  
		        ServletOutputStream out=null;
				try {
					out = response.getOutputStream();
					ImageIO.write(image, "JPEG", out);
				    out.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally {
					try {
						if(out != null) {
							out.close();
						}
					
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			
		}

}
