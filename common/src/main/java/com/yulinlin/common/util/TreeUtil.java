package com.yulinlin.common.util;

import com.yulinlin.common.domain.ITreeNode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class TreeUtil {

    public static  <E extends ITreeNode> List<E> sort(List<E> data, Comparator<E> comparator){
        data.sort(comparator);
        for (E e : data) {
            e.sort(comparator);
        }
        return data;
    }
    /**
     * 构造一棵树
     * @param data
     * @param <E>
     * @return
     */
    public static  <E extends ITreeNode> List<E> buildTree(List<E> data){
        HashMap<String,E> map = new HashMap<>(data.size());
        for (E e : data) {
            map.put(e.getId(),e);

        }
        List<E> list =  new LinkedList<>();

        for (E e : data) {
            String parentId =  e.getParentId();
            E parent = map.get(parentId);

            if(parent == null){
                list.add(e);
            }else{
                parent.addChildren(e);

            }
        }

        return  list;
    }


}
