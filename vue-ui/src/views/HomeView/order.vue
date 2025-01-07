<script>
import { useStore } from "vuex";
import { ref, onMounted, watch, computed } from "vue";
import { queryMinePassenger } from '@/api/passenger';
import { message } from "ant-design-vue";
import { addOrderList } from "@/api/Order";

const SEAT_TYPE_ARRAY = [
  { type: "YDZ", code: "1", desc: "一等座", price: "0.4" },
  { type: "EDZ", code: "2", desc: "二等座", price: "0.3" },
  { type: "RW", code: "3", desc: "软卧", price: "0.6" },
  { type: "YW", code: "4", desc: "硬卧", price: "0.5" }
];

const PASSENGER_TYPE_ARRAY = [
  { code: "1", desc: "成人" },
  { code: "2", desc: "学生" },
  { code: "3", desc: "儿童" },
  { code: "4", desc: "残疾军人" }
];

export default {
  name: 'OrderView',
  setup() {
    const store = useStore();
    const GetDailyTicker = store.state.Session_params.order;
    const Passenger = ref([]);
    const PassengerOptions = ref([]);
    const PassengerChecks = ref([]);
    const tickets = ref([]);
    const visible = ref(false);
    const chooseSeatType = ref(0);
    const SEAT_COL_ARRAY = [
      { type: "1", desc: "A", code: "A" },
      { type: "1", desc: "C", code: "C" },
      { type: "1", desc: "D", code: "D" },
      { type: "1", desc: "F", code: "F" },
      { type: "2", desc: "A", code: "A" },
      { type: "2", desc: "B", code: "B" },
      { type: "2", desc: "C", code: "C" },
      { type: "2", desc: "D", code: "D" },
      { type: "2", desc: "F", code: "F" },
    ];

    const COL_ARRAY = computed(() => {
      return SEAT_COL_ARRAY.filter(item => item.type === chooseSeatType.value);
    });

    const chooseSeatObj = ref({});
    watch(COL_ARRAY, () => {
      chooseSeatObj.value = {};
      for (let i = 1; i <= 2; i++) {
        COL_ARRAY.value.forEach(item => {
          chooseSeatObj.value[item.code + i] = false;
        });
      }
    }, { immediate: true });

    const seatType = SEAT_TYPE_ARRAY.reduce((acc, seat) => {
      const key = seat.type.toLowerCase();
      if (GetDailyTicker[key] >= 0) {
        acc.push({
          type: seat.type,
          code: seat.code,
          desc: seat.desc,
          count: GetDailyTicker[key],
          price: GetDailyTicker[key + "Price"],
        });
      }
      return acc;
    }, []);

    watch(PassengerChecks, () => {
      tickets.value = PassengerChecks.value.map(item => ({
        passengerId: item.id,
        passengerType: item.type,
        seatTypeCode: seatType[0]?.code || '',
        passengerName: item.name,
        passengerIdCard: item.idCard
      }));
    });

    const finishCheckPassenger = () => {
      if (tickets.value.length > 5) {
        message.error('最多只能购买5张车票');
        return;
      }
// 校验余票是否充足，购票列表中的每个座位类型，都去车次座位余票信息中，看余票是否充足
      // 前端校验不一定准，但前端校验可以减轻后端很多压力
      // 注意：这段只是校验，必须copy出seatTypesTemp变量来扣减，用原始的seatType去扣减，会影响真实的库存
      let seatTypesTemp =window.Tool.copy(seatType);
      for (let i = 0; i < tickets.value.length; i++) {
        let ticket = tickets.value[i];
        for (let j = 0; j < seatTypesTemp.length; j++) {
          let seatType = seatTypesTemp[j];
          // 同类型座位余票-1，这里扣减的是临时copy出来的库存，不是真正的库存，只是为了校验
          if (ticket.seatTypeCode === seatType.code) {
            seatType.count--;
            if (seatType.count < 0) {
              message.error(seatType.desc + '余票不足');
              return;
            }
          }
        }
      }
      console.log("前端余票校验通过");

      // 判断是否支持选座，只有纯一等座和纯二等座支持选座
      // 先筛选出购票列表中的所有座位类型，比如四张表：[1, 1, 2, 2]
      let ticketSeatTypeCodes = [];
      for (let i = 0; i < tickets.value.length; i++) {
        let ticket = tickets.value[i];
        ticketSeatTypeCodes.push(ticket.seatTypeCode);
      }
      // 为购票列表中的所有座位类型去重：[1, 2]
      const ticketSeatTypeCodesSet = Array.from(new Set(ticketSeatTypeCodes));
      console.log("选好的座位类型：", ticketSeatTypeCodesSet);
      if (ticketSeatTypeCodesSet.length !== 1) {
        console.log("选了多种座位，不支持选座");
        chooseSeatType.value = 0;
      } else {
        const SEAT_TYPE = {
          YDZ: {
            code: "1",
            desc: "一等座"
          },
          EDZ: {
            code: "2",
            desc: "二等座"
          }
        };
        // ticketSeatTypeCodesSet.length === 1，即只选择了一种座位（不是一个座位，是一种座位）
        if (ticketSeatTypeCodesSet[0] === SEAT_TYPE.YDZ.code) {
          console.log("一等座选座");
          chooseSeatType.value = SEAT_TYPE.YDZ.code;
        } else if (ticketSeatTypeCodesSet[0] === SEAT_TYPE.EDZ.code) {
          console.log("二等座选座");
          chooseSeatType.value = SEAT_TYPE.EDZ.code;
        } else {
          console.log("不是一等座或二等座，不支持选座");
          chooseSeatType.value = 0;
        }

        // 余票小于20张时，不允许选座，否则选座成功率不高，影响出票
        if (chooseSeatType.value !== 0) {
          for (let i = 0; i < seatType.length; i++) {
            let seatTypes = seatType[i];
            // 找到同类型座位
            if (ticketSeatTypeCodesSet[0] === seatTypes.code) {
              // 判断余票，小于20张就不支持选座
              if (seatTypes.count < 20) {
                console.log("余票小于20张就不支持选座")
                chooseSeatType.value = 0;
                break;
              }
            }
          }
        }
      }

      // 弹出确认界面
      visible.value = true;

    };


    const handleOk = async () => {


     console.log("选好的座位：", chooseSeatObj.value);

      // 设置每张票的座位
      // 先清空购票列表的座位，有可能之前选了并设置座位了，但选座数不对被拦截了，又重新选一遍
      for (let i = 0; i < tickets.value.length; i++) {
        tickets.value[i].seat = null;
      }
      let i = -1;
      // 要么不选座位，要么所选座位应该等于购票数，即i === (tickets.value.length - 1)
      for (let key in chooseSeatObj.value) {
        if (chooseSeatObj.value[key]) {
          i++;
          if (i > tickets.value.length - 1) {
            message.error( '所选座位数大于购票数');
            return;
          }
          tickets.value[i].seat = key;
        }
      }
      if (i > -1 && i < (tickets.value.length - 1)) {
        message.error( '所选座位数小于购票数');
        return;
      }

      console.log("最终购票：", tickets.value);

      const param = {
        dailyTrainTicketId: GetDailyTicker.id,
        date: GetDailyTicker.date,
        trainCode: GetDailyTicker.trainCode,
        start: GetDailyTicker.start,
        end: GetDailyTicker.end,
        tickets: tickets.value,
        seat: chooseSeatType.value
      };

      const res = await addOrderList(param);
      if (res.code === 200) {
        message.success('购票成功');
        visible.value = false;
      }
    };

    const OnQueryMine = async () => {
      const res = await queryMinePassenger();
      if (res.code === 200) {
        Passenger.value = res.content;
        PassengerOptions.value = Passenger.value.map(item => ({
          value: item,
          label: item.name
        }));
      }
    };

    onMounted(() => {
      OnQueryMine();
    });

    return {
      GetDailyTicker,
      seatType,
      Passenger,
      OnQueryMine,
      PassengerOptions,
      PassengerChecks,
      tickets,
      PASSENGER_TYPE_ARRAY,
      finishCheckPassenger,
      visible,
      chooseSeatObj,
      SEAT_COL_ARRAY,
      COL_ARRAY,
      chooseSeatType,
      handleOk
    };
  },
};
</script>

<template>
  <div class="order-train">
    <span class="order-train-main">{{ GetDailyTicker.date }}</span>&nbsp;
    <span class="order-train-main">{{ GetDailyTicker.trainCode }}</span>&nbsp;
    <span class="order-train-main">{{ GetDailyTicker.start }}</span>&nbsp;
    <span class="order-train-main">{{ GetDailyTicker.startTime }}</span>&nbsp;
    <span class="order-train-main">——</span>&nbsp;
    <span class="order-train-main">{{ GetDailyTicker.end }}</span>&nbsp;
    <span class="order-train-main">{{ GetDailyTicker.endTime }}</span>&nbsp;

    <div class="order-train-tickets">
      <div v-for="(item, index) in seatType" :key="index">
        <span>{{ item.desc }}</span>&nbsp;
        <span class="order-train-tickets-main">{{ item.price }}￥</span>&nbsp;
        <span class="order-train-tickets-main">{{ item.count }}</span>&nbsp;张票
      </div>
    </div>
    <a-divider></a-divider>
    <b>勾选需要购票的乘客</b>
    <a-checkbox-group v-model:value="PassengerChecks" :options="PassengerOptions"/>
    <div class="order-tickets">
      <a-row class="order-tickets-header" v-if="tickets.length > 0">
        <a-col :span="2">乘客</a-col>
        <a-col :span="6">身份证</a-col>
        <a-col :span="4">票种</a-col>
        <a-col :span="4">座位类型</a-col>
      </a-row>
      <a-row class="order-tickets-row" v-for="ticket in tickets" :key="ticket.PassengerId">
        <a-col :span="2">{{ ticket.passengerName }}</a-col>
        <a-col :span="6">{{ ticket.passengerIdCard }}</a-col>
        <a-col :span="4">
          <a-select v-model:value="ticket.passengerType" style="width: 100%">
            <a-select-option v-for="item in PASSENGER_TYPE_ARRAY" :key="item.code" :value="item.code">
              {{ item.desc }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :span="4">
          <a-select v-model:value="ticket.seatTypeCode" style="width: 100%">
            <a-select-option v-for="item in seatType" :key="item.code" :value="item.code">
              {{ item.desc }}
            </a-select-option>
          </a-select>
        </a-col>
      </a-row>
      <div v-if="tickets.length > 0">
        <a-button type="primary" size="large" @click="finishCheckPassenger">提交订单</a-button>
      </div>

      <a-modal v-model:visible="visible" title="请核对以下信息"
               style="top: 50px; width: 800px"
               ok-text="确认" cancel-text="取消"
               @ok="handleOk">
        <div class="order-tickets">
          <a-row class="order-tickets-header" v-if="tickets.length > 0">
            <a-col :span="3">乘客</a-col>
            <a-col :span="15">身份证</a-col>
            <a-col :span="3">票种</a-col>
            <a-col :span="3">座位类型</a-col>
          </a-row>
          <a-row class="order-tickets-row" v-for="ticket in tickets" :key="ticket.PassengerId">
            <a-col :span="3">{{ ticket.passengerName }}</a-col>
            <a-col :span="15">{{ ticket.passengerIdCard }}</a-col>
            <a-col :span="3">
          <span v-for="item in PASSENGER_TYPE_ARRAY" :key="item.code">
            <span v-if="item.code === ticket.passengerType">
              {{ item.desc }}
            </span>
          </span>
            </a-col>
            <a-col :span="3">
          <span v-for="item in seatType" :key="item.code">
            <span v-if="item.code === ticket.seatTypeCode">
              {{ item.desc }}
            </span>
          </span>
            </a-col>
          </a-row>
          <br/>
          <div v-if="chooseSeatType === 0" style="color: red;">
            您购买的车票不支持选座
            <div>12306规则：只有全部是一等座或全部是二等座才支持选座</div>
            <div>12306规则：余票小于一定数量时，不允许选座（本项目以20为例）</div>
          </div>
          <div v-else style="text-align: center">
            <a-switch class="choose-seat-item" v-for="item in COL_ARRAY" :key="item.code"
                      v-model:checked="chooseSeatObj[item.code + '1']" :checked-children="item.desc"
                      :un-checked-children="item.desc"/>
            <div v-if="tickets.length > 1">
              <a-switch class="choose-seat-item" v-for="item in COL_ARRAY" :key="item.code"
                        v-model:checked="chooseSeatObj[item.code + '2']" :checked-children="item.desc"
                        :un-checked-children="item.desc"/>
            </div>
            <div>{{ tickets }}</div>
            <div style="color: #999999">提示：您可以选择{{ tickets.length }}个座位</div>
          </div>
          最终购票：{{ tickets }}
          最终选座：{{ chooseSeatObj }}
        </div>
      </a-modal>
    </div>
  </div>
</template>

<style scoped>
.order-train .order-train-main {
  font-size: 18px;
  font-weight: bold;
}

.order-train .order-train-tickets {
  margin-top: 15px;
}

.order-train .order-train-tickets .order-train-tickets-main {
  color: red;
  font-size: 18px;
}

.order-tickets {
  margin: 10px 0;
}

.order-tickets .ant-col {
  padding: 5px 10px;
}

.order-tickets .order-tickets-header {
  background-color: cornflowerblue;
  border: solid 1px cornflowerblue;
  color: white;
  font-size: 16px;
  padding: 5px 0;
}

.order-tickets .order-tickets-row {
  border: solid 1px cornflowerblue;
  border-top: none;
  vertical-align: middle;
  line-height: 30px;
}
</style>
