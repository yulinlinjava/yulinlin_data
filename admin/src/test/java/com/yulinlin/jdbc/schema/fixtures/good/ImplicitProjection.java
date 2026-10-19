package com.yulinlin.jdbc.schema.fixtures.good;

import com.yulinlin.data.core.anno.JoinTable;

/** Ordinary table mappings do not own schema unless they explicitly opt in. */
@JoinTable("ignored_default_table")
public class ImplicitProjection { }
