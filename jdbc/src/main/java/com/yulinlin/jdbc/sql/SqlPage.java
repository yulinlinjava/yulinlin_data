package com.yulinlin.jdbc.sql;

import com.yulinlin.data.core.node.INode;

/** Pagination fragment shared by SELECT and GROUP parsers. */
public record SqlPage(int page, int size) implements INode {}
