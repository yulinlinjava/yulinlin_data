package com.yulinlin.starter.domain;

import lombok.Data;

import java.util.List;

@Data
public class ListPo<E> {

    private List<E> list;

}
