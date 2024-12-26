package com.guo.common.resp;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/26
 */
@Getter
@Setter
@ToString
public class PageResp<T> implements Serializable {
    /**
     * 总条数
     */
    private Long total;
    /**
     * 数据集合
     */
    private List<T> list;


}
