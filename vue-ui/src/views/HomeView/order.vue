<script>
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useStore } from "vuex";
import { LoadingOutlined } from "@ant-design/icons-vue";
import { message } from "ant-design-vue";
import { queryMinePassenger } from "@/api/passenger";
import { addOrderList, getImageCode, queryOrderList } from "@/api/Order";

const SEAT_TYPE_ARRAY = [
  { type: "YDZ", code: "1", desc: "一等座" },
  { type: "EDZ", code: "2", desc: "二等座" },
  { type: "RW", code: "3", desc: "软卧" },
  { type: "YW", code: "4", desc: "硬卧" }
];

const PASSENGER_TYPE_ARRAY = [
  { code: "1", desc: "成人" },
  { code: "2", desc: "学生" },
  { code: "3", desc: "儿童" },
  { code: "4", desc: "残疾军人" }
];

const SEAT_COL_ARRAY = [
  { type: "1", code: "A", desc: "A" },
  { type: "1", code: "C", desc: "C" },
  { type: "1", code: "D", desc: "D" },
  { type: "1", code: "F", desc: "F" },
  { type: "2", code: "A", desc: "A" },
  { type: "2", code: "B", desc: "B" },
  { type: "2", code: "C", desc: "C" },
  { type: "2", code: "D", desc: "D" },
  { type: "2", code: "F", desc: "F" }
];

export default {
  name: "OrderView",
  setup() {
    const store = useStore();
    const dailyTicket = computed(() => store.state.Session_params.order || {});

    const passengers = ref([]);
    const passengerOptions = ref([]);
    const passengerChecks = ref([]);
    const tickets = ref([]);

    const visible = ref(false);
    const spinning = ref(false);
    const memberId = ref();
    const memberCount = ref(-1);

    const chooseSeatType = ref("0");
    const chooseSeatObj = ref({});

    const imageCode = ref("");
    const imageCodeToken = ref("");
    const imageCodeSrc = ref("");

    const indicator = h(LoadingOutlined, {
      style: { fontSize: "24px" },
      spin: true
    });

    let queryLineCountInterval = null;
    const MAX_POLL_COUNT = 60;

    const seatType = computed(() => {
      return SEAT_TYPE_ARRAY.reduce((acc, seat) => {
        const key = seat.type.toLowerCase();
        const count = Number(dailyTicket.value[key]);
        if (!Number.isNaN(count) && count >= 0) {
          acc.push({
            type: seat.type,
            code: seat.code,
            desc: seat.desc,
            count,
            price: dailyTicket.value[key + "Price"]
          });
        }
        return acc;
      }, []);
    });

    const colArray = computed(() => {
      return SEAT_COL_ARRAY.filter(item => item.type === chooseSeatType.value);
    });

    watch(colArray, () => {
      const next = {};
      for (let row = 1; row <= 2; row++) {
        colArray.value.forEach(item => {
          next[item.code + row] = false;
        });
      }
      chooseSeatObj.value = next;
    }, { immediate: true });

    watch(passengerChecks, () => {
      tickets.value = passengerChecks.value.map(item => ({
        passengerId: item.id,
        passengerType: item.type,
        passengerName: item.name,
        passengerIdCard: item.idCard,
        seatTypeCode: seatType.value[0]?.code || "",
        seat: null
      }));
    });

    const stopQueryLineCount = () => {
      if (queryLineCountInterval) {
        clearInterval(queryLineCountInterval);
        queryLineCountInterval = null;
      }
    };

    const loadImageCode = async () => {
      imageCodeToken.value = window.Tool.uuid(8);
      const res = await getImageCode(imageCodeToken.value);
      if (res && res.code === 200) {
        imageCodeSrc.value = "data:image/gif;base64," + res.content;
      } else {
        imageCodeSrc.value = "";
      }
    };

    const onQueryMine = async () => {
      const res = await queryMinePassenger();
      if (res && res.code === 200) {
        passengers.value = res.content;
        passengerOptions.value = passengers.value.map(item => ({
          value: item,
          label: item.name
        }));
      }
    };

    const finishCheckPassenger = async () => {
      if (tickets.value.length === 0) {
        message.error("请先选择乘客");
        return;
      }
      if (tickets.value.length > 5) {
        message.error("最多只能购买5张车票");
        return;
      }

      const seatTypesTemp = window.Tool.copy(seatType.value);
      for (let i = 0; i < tickets.value.length; i++) {
        const ticket = tickets.value[i];
        for (let j = 0; j < seatTypesTemp.length; j++) {
          const item = seatTypesTemp[j];
          if (ticket.seatTypeCode === item.code) {
            item.count--;
            if (item.count < 0) {
              message.error(item.desc + "余票不足");
              return;
            }
          }
        }
      }

      const seatTypeCodes = Array.from(new Set(tickets.value.map(item => item.seatTypeCode)));
      chooseSeatType.value = "0";
      if (seatTypeCodes.length === 1 && (seatTypeCodes[0] === "1" || seatTypeCodes[0] === "2")) {
        const currentSeatType = seatType.value.find(item => item.code === seatTypeCodes[0]);
        if (currentSeatType && currentSeatType.count >= 20) {
          chooseSeatType.value = seatTypeCodes[0];
        }
      }

      visible.value = true;
      await loadImageCode();
    };

    const handleOk = async () => {
      tickets.value.forEach(item => {
        item.seat = null;
      });

      let index = -1;
      for (const key in chooseSeatObj.value) {
        if (chooseSeatObj.value[key]) {
          index++;
          if (index > tickets.value.length - 1) {
            message.error("所选座位数大于购票数");
            return;
          }
          tickets.value[index].seat = key;
        }
      }
      if (index > -1 && index < tickets.value.length - 1) {
        message.error("所选座位数小于购票数");
        return;
      }

      const param = {
        dailyTrainTicketId: dailyTicket.value.id,
        date: dailyTicket.value.date,
        trainCode: dailyTicket.value.trainCode,
        start: dailyTicket.value.start,
        end: dailyTicket.value.end,
        tickets: tickets.value,
        seat: chooseSeatType.value,
        CodeToken: imageCodeToken.value,
        code: imageCode.value
      };

      const res = await addOrderList(param);
      if (res && res.code === 200) {
        memberId.value = res.content;
        memberCount.value = -1;
        spinning.value = true;
        visible.value = false;
        queryLineCount();
      } else if (res) {
        message.error(res.message || "提交订单失败");
      }
    };

    const queryLineCount = () => {
      stopQueryLineCount();
      let pollCount = 0;
      queryLineCountInterval = setInterval(async () => {
        pollCount++;
        const res = await queryOrderList(memberId.value);
        if (!res || res.code !== 200) {
          stopQueryLineCount();
          spinning.value = false;
          message.error(res?.message || "查询订单状态失败");
          return;
        }

        const result = res.content;
        switch (result) {
          case -3:
            message.success("购票成功");
            spinning.value = false;
            stopQueryLineCount();
            break;
          case -4:
            message.error("购票失败");
            spinning.value = false;
            stopQueryLineCount();
            break;
          case -5:
            message.error("抱歉，没票了");
            spinning.value = false;
            stopQueryLineCount();
            break;
          default:
            memberCount.value = result;
            if (pollCount >= MAX_POLL_COUNT) {
              message.warning("排队超时，请到我的订单页查看最终结果");
              spinning.value = false;
              stopQueryLineCount();
            }
            break;
        }
      }, 1000);
    };

    onMounted(() => {
      onQueryMine();
    });

    onBeforeUnmount(() => {
      stopQueryLineCount();
    });

    return {
      indicator,
      dailyTicket,
      seatType,
      passengers,
      passengerOptions,
      passengerChecks,
      tickets,
      visible,
      spinning,
      memberId,
      memberCount,
      chooseSeatType,
      chooseSeatObj,
      colArray,
      PASSENGER_TYPE_ARRAY,
      finishCheckPassenger,
      handleOk,
      loadImageCode,
      imageCode,
      imageCodeToken,
      imageCodeSrc
    };
  }
};
</script>

<template>
  <div class="order-train">
    <span class="order-train-main">{{ dailyTicket.date }}</span>
    <span class="order-train-main">{{ dailyTicket.trainCode }}</span>
    <span class="order-train-main">{{ dailyTicket.start }}</span>
    <span class="order-train-main">{{ dailyTicket.startTime }}</span>
    <span class="order-train-main">-</span>
    <span class="order-train-main">{{ dailyTicket.end }}</span>
    <span class="order-train-main">{{ dailyTicket.endTime }}</span>

    <div class="order-train-tickets">
      <div v-for="item in seatType" :key="item.code">
        <span>{{ item.desc }}</span>
        <span class="order-train-tickets-main">{{ item.price }}元</span>
        <span class="order-train-tickets-main">{{ item.count }}</span>
        <span>张票</span>
      </div>
    </div>

    <a-divider />
    <b>勾选需要购票的乘客</b>
    <a-checkbox-group v-model:value="passengerChecks" :options="passengerOptions" />

    <div class="order-tickets">
      <a-row v-if="tickets.length > 0" class="order-tickets-header">
        <a-col :span="4">乘客</a-col>
        <a-col :span="8">身份证</a-col>
        <a-col :span="4">票种</a-col>
        <a-col :span="4">座位类型</a-col>
      </a-row>

      <a-row v-for="ticket in tickets" :key="ticket.passengerId" class="order-tickets-row">
        <a-col :span="4">{{ ticket.passengerName }}</a-col>
        <a-col :span="8">{{ ticket.passengerIdCard }}</a-col>
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

      <a-modal
        v-model:visible="visible"
        title="请核对以下信息"
        style="top: 50px; width: 800px"
        ok-text="确认"
        cancel-text="取消"
        @ok="handleOk"
      >
        <div class="order-tickets">
          <a-row v-if="tickets.length > 0" class="order-tickets-header">
            <a-col :span="4">乘客</a-col>
            <a-col :span="10">身份证</a-col>
            <a-col :span="4">票种</a-col>
            <a-col :span="4">座位类型</a-col>
          </a-row>

          <a-row v-for="ticket in tickets" :key="ticket.passengerId" class="order-tickets-row">
            <a-col :span="4">{{ ticket.passengerName }}</a-col>
            <a-col :span="10">{{ ticket.passengerIdCard }}</a-col>
            <a-col :span="4">
              <span v-for="item in PASSENGER_TYPE_ARRAY" :key="item.code">
                <span v-if="item.code === ticket.passengerType">{{ item.desc }}</span>
              </span>
            </a-col>
            <a-col :span="4">
              <span v-for="item in seatType" :key="item.code">
                <span v-if="item.code === ticket.seatTypeCode">{{ item.desc }}</span>
              </span>
            </a-col>
          </a-row>

          <br />
          <div v-if="chooseSeatType === '0'" style="color: red;">
            当前车票不支持选座。
            <div>规则：只有全部为一等座或全部为二等座时支持选座。</div>
            <div>规则：余票低于一定数量时不允许选座，本项目阈值为 20。</div>
          </div>
          <div v-else style="text-align: center">
            <a-switch
              v-for="item in colArray"
              :key="item.code + '1'"
              v-model:checked="chooseSeatObj[item.code + '1']"
              class="choose-seat-item"
              :checked-children="item.desc"
              :un-checked-children="item.desc"
            />
            <div v-if="tickets.length > 1">
              <a-switch
                v-for="item in colArray"
                :key="item.code + '2'"
                v-model:checked="chooseSeatObj[item.code + '2']"
                class="choose-seat-item"
                :checked-children="item.desc"
                :un-checked-children="item.desc"
              />
            </div>
            <div style="color: #999">提示：您可以选择 {{ tickets.length }} 个座位</div>
          </div>

          <a-input v-model:value="imageCode" placeholder="请输入验证码" style="width: 500px">
            <template #suffix>
              <img v-show="!!imageCodeSrc" :src="imageCodeSrc" @click="loadImageCode()" />
            </template>
          </a-input>
        </div>
      </a-modal>

      <a-modal v-model:visible="spinning" title="正在处理..." :footer="null" :mask="null">
        <template v-if="memberCount <= 0">
          <a-spin :indicator="indicator" />
          正在排队出票
        </template>
        <template v-else>
          <a-spin :indicator="indicator" />
          当前前面还有 {{ memberCount }} 人
        </template>
      </a-modal>
    </div>
  </div>
</template>

<style scoped>
.order-train .order-train-main {
  font-size: 18px;
  font-weight: bold;
  margin-right: 8px;
}

.order-train .order-train-tickets {
  margin-top: 15px;
}

.order-train .order-train-tickets .order-train-tickets-main {
  color: red;
  font-size: 18px;
  margin: 0 6px;
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
  line-height: 30px;
}

.choose-seat-item {
  margin: 0 6px 8px;
}
</style>
