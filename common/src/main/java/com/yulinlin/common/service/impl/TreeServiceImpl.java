/*
package com.yulinlin.common.service.impl;

import com.yulinlin.common.domain.ISortValue;
import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.service.ITreeService;
import com.yulinlin.common.util.TreeUtil;
import com.yulinlin.data.lang.util.ListString;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.List;

public abstract class TreeServiceImpl<E extends TreeEntity<E>> extends ServiceImpl<E> implements ITreeService<E> {





    @Override
    public int save(E obj) {
        ListString<String> listString = obj.getParentPath();
        if(listString  != null && !listString.isEmpty()){
            obj.setParentId(listString.get(listString.size() - 1));
        }
        return super.save(obj);
    }

    @Override
    public int insertOne(E obj) {


        if(ReflectionUtil.isEmpty(obj.getParentId())){
            obj.setParentId(TreeEntity.root);
        }else{
            changeParent(obj.findParent(),1,true);
        }
        int total = super.insertOne(obj);

        return total;
    }

    @Override
    public int deleteByIdIn(List<String> coll) {
        for (E e : findByIdIn(coll)) {
            changeParent(e.findParent(),e.getChildrenSize(),false);
        }

        int total =  super.deleteByIdIn(coll);

        return total;
    }

    private void changeParent(E node, int size,boolean isInc){

        if(node == null){
            return;
        }
            if(!isInc){
                size = -size;
            }
            ModelUpdateWrapper.newWrapper(getEntityClass())
                    .inc("childrenSize",size)
                    .in(node.getParentPath())
                    .execute();

    }

    private void changeChild(E node){
        if(node.getId() == E.root){
            return;
        }
        ListString listString = new ListString();
        listString.addAll(node.getParentPath());
        listString.add(node.getId());
        for (E child : node.findChildren()) {
            child.setParentPath(listString);
            child.createUpdateWrapper().execute();
            changeChild(child);
        }

    }

    @Override
    public int updateOne(E obj) {

        E old =  this.findById(obj.getId());

        if(obj.getParentPath() != null && !obj.getParentPath().isEmpty()){
            ListString<String> list = obj.getParentPath();
            obj.setParentId(list.get(list.size() - 1));
        }else{
            obj.setParentId(TreeEntity.root);
        }

        if( !obj.getParentId().equals(old.getParentId())){
            changeParent(old.findParent(),old.getChildrenSize()+1,false);
            //新父孩子数量
            changeParent(obj.findParent(),old.getChildrenSize() + 1,true);

        }

        int total  =  super.updateOne(obj);

        if( !obj.getParentId().equals(old.getParentId())){
            changeChild(obj);
        }

        return  total;

    }

    @Override
    public List<E> getTree(E query) {
        ModelSelectWrapper<E> wrapper =   ModelSelectWrapper.newInstance(this.getEntityClass(),query);
        List<E> list =  TreeUtil.buildTree( wrapper.selectList());
        if(ISortValue.class.isAssignableFrom(this.getEntityClass())){
            TreeUtil.sort(list,(x,y) -> {
                ISortValue x0 = (ISortValue)x;
                ISortValue y0 = (ISortValue)y;
                return  x0.getSortValue() - y0.getSortValue();
            });
        }
        return list;
    }

    @Override
    public int saveTree(E tree) {

       insertOne(tree);
       int total = 0;

        if(tree.getChildren() != null){
            for (E e : tree.getChildren()) {
                e.setParentId(
                        tree.getId()
                );
                total+= saveTree(e);
            }
        }

        return total;
    }


    @Override
    public int saveTreeList(List<E> tree) {
        int total = 0;
        for (E e : tree) {
            e.setParentId(TreeEntity.root);
            total+= save(e);
        }

        return total;
    }



}
*/
