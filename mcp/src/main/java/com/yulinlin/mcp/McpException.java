package com.yulinlin.mcp;
public class McpException extends RuntimeException {
 private final int status;
 public McpException(int status,String message){super(message);this.status=status;}
 public int status(){return status;}
}
