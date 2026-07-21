package com.yulinlin.admin;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.ListString;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Simple video entity for demo.
 */

@Data
@ApiModel("视频")
@JoinTable("video_info")
public class VideoEntity implements AbstractModel<VideoEntity> {

    @JoinField
    @JoinMeta(primaryKey = true)
    private Long id;


    private LongAdder totalShows;

    @ApiModelProperty("圈子ID")
    private Long circleId;


    @ApiModelProperty("创作者ID")
    private Long creatorId;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("标签列表")
    private ListString<String> tags;


    @JsonIgnore
    @JoinField(exist = false)
    private Set<String> tagSet;



    @ApiModelProperty("热度得分")
    private DoubleAdder hotScore ;

    private Integer totalLikes;

    private Integer totalCollects;

    private Long collectionId;

    @ApiModelProperty("视频时长（秒）")
    private Double duration;

    private DateTime crtTime;

    @JoinField(exist = false)
    UserInfo userInfo;


    @JoinField(exist = false)
    boolean like;

    @JoinField(exist = false)
    boolean collect;

    public Set<String> tagSet(){
        if(tagSet == null){
            tagSet = new HashSet<>(tags);
        }
        return tagSet;
    }

    public void increaseHot( double delta) {
        hotScore.add(delta);
    }

    public void decreaseHot( double delta) {
        hotScore.add(-delta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoEntity that = (VideoEntity) o;
        return Objects.equals(id, that.id);
    }
}