package com.guo.business.mapper;

import com.guo.business.domain.ChangeRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChangeRecordMapper {

    int insert(ChangeRecord record);

    int updateByPrimaryKeySelective(ChangeRecord record);

    ChangeRecord selectByPrimaryKey(Long id);

    List<ChangeRecord> selectByMember(@Param("memberId") Long memberId);

    List<ChangeRecord> selectByOriginalOrder(@Param("originalOrderId") Long originalOrderId);

    List<ChangeRecord> selectAll();

    long countAll();
}
