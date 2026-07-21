package com.yulinlin.common.domain;


import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.lang.util.ListString;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public  class TreeEntity<E extends TreeEntity<E>> extends SuperEntity<E> implements ITreeNode<E> {

    public static String root="0";


    private static ListString parent_path = new ListString();

    @ApiModelProperty("父路径")
    private ListString parentPath;

    @ApiModelProperty("父id")
    private String parentId;



    @JoinField(exist = false)
    private List<E> children;

    @Override
    public void addChildren(E node) {
        if(children == null){
            children = new ArrayList<>();
        }
        if(!children.contains(node)){
            children.add(node);
        }

    }

    @Override
    public void insertBefore() {
        if(parentId == null){
            parentId = root;
        }
        if(parentPath == null){
            parentPath = parent_path;
        }
        super.insertBefore();
    }

    @Override
    public void sort(Comparator<ITreeNode> comparator) {

        if(children != null){
            children.sort(comparator);
            for (E child : children) {
                child.sort(comparator);
            }
        }

    }

    public static String getRoot() {
        return root;
    }

    public static void setRoot(String root) {
        TreeEntity.root = root;
    }

    public static ListString getParent_path() {
        return parent_path;
    }

    public static void setParent_path(ListString parent_path) {
        TreeEntity.parent_path = parent_path;
    }

    public ListString getParentPath() {
        return parentPath;
    }

    public void setParentPath(ListString parentPath) {
        this.parentPath = parentPath;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }


    public List<E> getChildren() {
        return children;
    }

    public void setChildren(List<E> children) {
        this.children = children;
    }

    public List<E> findChildren(){
        return (List<E>)ModelSelectWrapper.newInstance(this.getClass()).eq("parentId",getId()).selectList();
    }

    public E findParent(){
        if(parentId == root){
            return null;
        }
        return (E)ModelSelectWrapper.newInstance(this.getClass()).eq("id",parentId).selectOne();
    }
}
