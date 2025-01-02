package com.guo.business.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
@Getter
@Setter
@ToString
public class Daily_TrainQueryReq extends PageReq {

   private String code;
   @DateTimeFormat(pattern = "yyyy-MM-dd")
   private Date date;
}
