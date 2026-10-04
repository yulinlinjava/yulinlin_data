package com.yulinlin.common.service.impl2;

import com.yulinlin.common.domain.ISortValue;
import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.service.ITreeService;
import com.yulinlin.common.util.TreeUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.ListString;

import java.util.ArrayList;
import java.util.List;

public abstract class TreeImpl<E extends TreeEntity<E>> extends ServiceImpl<E> implements ITreeService<E> {





    @Override
    public int save(E obj) {
        ListString<String> listString = obj.getParentPath();
        if(listString  != null && !listString.isEmpty()){
            obj.setParentId(listString.get(listString.size() - 1));
        }else {
            obj.setParentId(TreeEntity.root);
            obj.setParentPath(new ListString());
        }
        return super.save(obj);
    }

    @Override
    public int insertOne(E obj) {


        if(ReflectionUtil.isEmpty(obj.getParentId())){
            obj.setParentId(TreeEntity.root);
        }else{

        }
        int total = super.insertOne(obj);

        return total;
    }

    @Override
    public int deleteByIdIn(List<String> coll) {



        for (E e : findByIdIn(coll)) {
            e.getParentPath().add(e.getId());
            ModelDeleteWrapper.newInstance(getEntityClass())
                    .likeRight("parentPath",e.getParentPath().encode())
                    .execute();

        }

        int total =  super.deleteByIdIn(coll);

        return total;
    }



    private void changeChild(E node,List<E> data){

        ListString listString = new ListString();
        listString.addAll(node.getParentPath());
        listString.add(node.getId());
        List<E> children = node.findChildren();

        if(children.size() > 0){
            for (E child : children) {
                child.setParentPath(listString);
                changeChild(child,data);
            }
        }

        data.addAll(children);
    }

    @Override
    public int updateOne(E obj) {

        E old =  this.findById(obj.getId());

        if(obj.getParentPath() != null && !obj.getParentPath().isEmpty()){
            ListString<String> list = obj.getParentPath();
            obj.setParentId(list.get(list.size() - 1));
        }else{
            obj.setParentId(TreeEntity.root);
            obj.setParentPath(new ListString());
        }



        int total  =  super.updateOne(obj);

        if( !obj.getParentId().equals(old.getParentId())){
            ArrayList<E> objects = new ArrayList<>();


            changeChild(obj,objects);
            if(objects.size() > 0){
                changeChild(obj,objects);
                ModelUpdateWrapper.newInstance(objects).batch().execute();
            }


        }

        return  total;

    }


    @Override
    public List<E> getTree(Object query) {
        List<E> data =

                ModelSelectWrapper.newInstance(this.getEntityClass(),query).selectList();
        List<E> tree =  TreeUtil.buildTree( data);
        if(ISortValue.class.isAssignableFrom(this.getEntityClass())){
            TreeUtil.sort(tree,(x,y) -> {
                ISortValue x0 = (ISortValue)x;
                ISortValue y0 = (ISortValue)y;
                return  x0.getSortValue() - y0.getSortValue();
            });
        }
        return tree;
    }






    @Override
    public int saveList(List<E> coll) {


        return super.saveList(coll);
    }
}
