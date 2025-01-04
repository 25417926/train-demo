<script>
import {useStore} from "vuex"

export default {
  name: 'OrderView',
  setup() {
    const SEAT_TYPE_ARRAY = [
      {type: "YDZ", code: "1", desc: "一等座", price: "0.4"},
      {type: "EDZ", code: "2", desc: "二等座", price: "0.3"},
      {type: "RW", code: "3", desc: "软卧", price: "0.6"},
      {type: "YW", code: "4", desc: "硬卧", price: "0.5"}
    ];
    const store = useStore()
    // 从 Vuex store 中获取每日票价信息
    const GetDailyTicker = store.state.Session_params.order;

    // 初始化一个空数组，用于存储处理后的座位类型信息
    const seatType = [];

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

    // 输出处理后的座位类型信息到控制台，以便调试
    console.log(seatType);

    // 返回一个对象，包含处理后的每日票价信息和座位类型信息
    return {
      GetDailyTicker,
      seatType,
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

    <div class="order-train-ticker">
      <div v-for="(item, index) in seatType" :key="index">
        <span >{{ item.desc }}</span>&nbsp;
        <span class="order-train-ticker-main">{{ item.price }}￥</span>&nbsp;
        <span class="order-train-ticker-main">{{ item.count }}</span>&nbsp;张票
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-train .order-train-main {
  font-size: 18px;
  font-weight: bold;
}
.order-train .order-train-ticker {
  margin-top: 15px;
}
.order-train .order-train-ticker .order-train-ticker-main {
  color: red;
  font-size: 18px;
}
</style>