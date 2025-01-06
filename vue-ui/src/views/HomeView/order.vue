<script>
import {useStore} from "vuex"
import {ref, onMounted, watch, computed} from "vue";
import {queryMinePassenger} from '@/api/passenger'
import {message} from "ant-design-vue";
import {addOrderList} from "@/api/Order"
export default {
  name: 'OrderView',
  setup() {
    const SEAT_TYPE_ARRAY = [
      {type: "YDZ", code: "1", desc: "一等座", price: "0.4"},
      {type: "EDZ", code: "2", desc: "二等座", price: "0.3"},
      {type: "RW", code: "3", desc: "软卧", price: "0.6"},
      {type: "YW", code: "4", desc: "硬卧", price: "0.5"}
    ];
    const PASSENGER_TYPE_ARRAY = [
      {code: "1", desc: "成人"},
      {code: "2", desc: "学生"},
      {code: "3", desc: "儿童"},
      {code: "4", desc: "残疾军人"}
    ];

    const Passenger = ref([])
    const store = useStore()
    const PassengerOptions = ref([])
    const PassengerChecks = ref([])
    const tickets = ref({})
    let visible = ref(false)

    // 从 Vuex store 中获取每日票价信息
    const GetDailyTicker = store.state.Session_params.order;

    // 初始化一个空数组，用于存储处理后的座位类型信息
    const seatType = [];
    // 0: 不支持选座; 1: 选一等座; 2: 选二等座
    const chooseSeatType = ref(0);
    const SEAT_COL_ARRAY = [
      {type: "1", desc: "A", code: "A"},
      {type: "1", desc: "C", code: "C"},
      {type: "1", desc: "D", code: "D"},
      {type: "1", desc: "F", code: "F"},
      {type: "2", desc: "A", code: "A"},
      {type: "2", desc: "B", code: "B"},
      {type: "2", desc: "C", code: "C"},
      {type: "2", desc: "D", code: "D"},
      {type: "2", desc: "F", code: "F"},
    ];

    // 根据选择的座位类型，计算出对应的列，比如要选的是一等座，就筛选出ACDF，要选的是二等座，就筛选出ABCDF
    const COL_ARRAY = computed(() => {
      return SEAT_COL_ARRAY.filter(item => item.type === chooseSeatType.value);
    });

    // 选择的座位
    const chooseSeatObj = ref({});
    watch(() => COL_ARRAY.value, () => {
      chooseSeatObj.value = {}
      for (let i = 1; i <= 2; i++) {
        COL_ARRAY.value.forEach(item => {
          chooseSeatObj.value[item.code + i] = false;
        });
      }
    });

    console.log("初始化两排座位，都是未选中:", chooseSeatObj.value, {immediate: true});

    // 遍历座位类型数组，以获取每个座位类型的信息
    for (let KEY in SEAT_TYPE_ARRAY) {
      // 将座位类型转换为小写，以匹配每日票价信息中的键
      let key = SEAT_TYPE_ARRAY[KEY].type.toLowerCase();

      // 检查每日票价信息中是否存在当前座位类型的记录，并且记录的值大于等于0
      if (GetDailyTicker[key] >= 0) {
        // 将当前座位类型的信息添加到seatType数组中，包括类型、代码、描述、数量和价格
        seatType.push({
          type: SEAT_TYPE_ARRAY[KEY].type,
          code: SEAT_TYPE_ARRAY[KEY].code,
          desc: SEAT_TYPE_ARRAY[KEY].desc,
          count: GetDailyTicker[key],
          price: GetDailyTicker[key + "Price"],
        });
      }
    }
    watch(() => PassengerChecks.value, () => {

      tickets.value = {}

      tickets.value = PassengerChecks.value.map(item => {
        return {
          PassengerId: item.id,
          PassengerType: item.type,
          seatTypeCode: seatType[0].code,
          PassengerName: item.name,
          PassengerIdCard: item.idCard,
        }
      })

    })
    const finishCheckPassenger = () => {
      console.log("购票列表：", tickets.value);

      if (tickets.value.length > 5) {
        message.error('最多只能购买5张车票');
        return;
      }
      //校验余票是否充足，购票列表中的每个座位类型都需要去车次座位余票信息内查看余票数量是否足够
      //提供校验，降低后端压力
      let seatTypesTemp = window.Tool.copy(seatType);

      for (let i = 0; i < tickets.value.length; i++) {
        let ticket = tickets.value[i]
        for (let j = 0; j < seatTypesTemp.length; j++) {
          let seatTypes = seatTypesTemp[j]
          if (ticket.seatTypeCode === seatTypes.code) {
            if (seatTypes.count < 0) {
              message.error('余票不足');
              return;
            } else {
              seatTypes.count -= 1
            }
          }
        }
      }
      console.log("校验通过")

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
        // ticketSeatTypeCodesSet.length === 1，即只选择了一种座位（不是一个座位，是一种座位）
        // 判断选择的座位类型是否是纯一等座或纯二等座
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
      //弹出确认界面
      visible.value = true
    }
    const handleOk = async () => {
      // if (Tool.isEmpty(imageCode.value)) {
      //   notification.error({description: '验证码不能为空'});
      //   return;
      // }

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
            message.error('所选座位数大于购票数');
            return;
          }
          tickets.value[i].seat = key;
        }
      }
      if (i > -1 && i < (tickets.value.length - 1)) {
        message.error('所选座位数小于购票数');
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
      }
      const res = await addOrderList(param)
      console.log("res", res)
      if (res.code === 200) {
        message.success('购票成功');
        visible.value = false
      }
    }
    const OnQueryMine = async () => {
      const res = await queryMinePassenger()
      if (res.code === 200) {
        Passenger.value = res.content
        PassengerOptions.value = Passenger.value.map(item => {
          return {
            value: item,
            label: item.name
          }
        })

      }
    }
    onMounted(() => {
      OnQueryMine()
    })
// 返回一个对象，包含处理后的每日票价信息和座位类型信息
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
}
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
        <a-col :span="2">{{ ticket.PassengerName }}</a-col>
        <a-col :span="6">{{ ticket.PassengerIdCard }}</a-col>
        <a-col :span="4">
          <a-select v-model:value="ticket.PassengerType" style="width: 100%">
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
            <a-col :span="3">{{ ticket.PassengerName }}</a-col>
            <a-col :span="15">{{ ticket.PassengerIdCard }}</a-col>
            <a-col :span="3">
          <span v-for="item in PASSENGER_TYPE_ARRAY" :key="item.code">
            <span v-if="item.code === tickets.PassengerType">
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
            <div>{{ tickets.value }}</div>
            <div style="color: #999999">提示：您可以选择{{ tickets.length }}个座位</div>
          </div>
          <!--          <br>-->
          <!--          <div style="color: red">-->
          <!--            体验排队购票，加入多人一起排队购票：-->
          <!--            <a-input-number v-model:value="lineNumber" :min="0" :max="20" />-->
          <!--          </div>-->
          <!--<br/>-->
          最终购票：{{tickets}}
          最终选座：{{chooseSeatObj}}
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