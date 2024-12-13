<script >
export default {
  name: 'loginView',
  data() {
    return {
      mobile: null,
      pwd:null,
      code:null
    }
  }
}
</script>

<template>
  <div class="login">
    <div class="login_box">
      <!-- required就是不能为空  必须在css效果中有很大的作用 -->
      <input type="text" name='name' v-model="mobile"  required />
      <label for="name">手机号</label>
    </div>
    <div class="login_box">
      <input type="password" name='pwd' v-model="pwd" required>
      <label for="pwd">密码</label>
    </div>
    <div class="code_box">
      <input type="text" name='code' v-model="code" required>
      <label for="code">验证码</label>
      <a-button>发送验证码</a-button>
    </div>
    <a href="#">
      登录
    </a>

  </div>
</template>

<style>
  .login{
    width: 300px;
  }
  .login .login_box {
    /*相对定位*/
    position: relative;
    width: 100%;
  }
  .login .code_box {
    /*相对定位*/
    position: relative;
  }
  .login .login_box input{
    /*清除input框自带的边框和轮廓*/
    outline: none;
    border: none;
    width: 100%;
    padding: 10px 0;
    margin-bottom: 30px;
    color: #fff;
    font-size: 16px;
    border-bottom: 1px solid #fff;
    /*背景颜色为透明色*/
    background-color: transparent;
    transition: border-bottom 1s;
  }
  .login .login_box input:hover,
  .login .login_box input:focus{
    border-bottom:1px solid #03e9f4;
  }
  .login .code_box input{
    /*清除input框自带的边框和轮廓*/
    position: relative;
    left:   0px;
    outline: none;
    border: none;
    width: 60%;
    padding: 10px 0;
    margin-bottom: 30px;
    color: #fff;
    font-size: 16px;
    border-bottom: 1px solid #fff;
    /*背景颜色为透明色*/
    background-color: transparent;
    transition: border-bottom 1s;
  }
  .login .code_box input:hover,
  .login .code_box input:focus{
    border-bottom:1px solid #03e9f4;
  }
  .login .login_box label{
    position:absolute;
    top: 0 ;
    left: 0;
    padding: 10px 0;
    color: #fff;
    /*这个属性的默认值是auto 默认是这个元素可以被点击
    但是如果我们写了none  就是这个元素不能被点击，就好像它可见但是不能用
    可望而不可及*/
    /*这个就是两者的区别*/
    pointer-events: none;
    /*加个过度*/
    transition: all 0.5s;
  }
  .login .code_box label{
    position:absolute;
    top: 0 ;
    left: 0;
    padding: 10px 0;
    color: #fff;
    /*这个属性的默认值是auto 默认是这个元素可以被点击
    但是如果我们写了none  就是这个元素不能被点击，就好像它可见但是不能用
    可望而不可及*/
    /*这个就是两者的区别*/
    pointer-events: none;
    /*加个过度*/
    transition: all 0.5s;
  }
  /*: focus 选择器是当input获得焦点是触发的样式 + 是相邻兄弟选择器
    去找与input相邻的兄弟label*/
  /*：valid 选择器是判断input 框的内容是否合法，如果合法会执行下面的属性代码，
    不合法就不会执行，我们刚开始写布局的时候给input框写了required 我们删掉看对比
    当没有required的话   input框的值就会被认为一直合法，所以一直都是下方的样式，
    但是密码不会，密码框的值为空，那么这句话就不合法，required不能为空
    当我们给密码框写点东西的时候才会执行以下代码

  */
  .login .login_box input:focus + label,
  .login .login_box input:hover + label,
  .login  .login_box input:valid + label{
    top: -20px;
    color: #03e9f4;
    font-size: 12px;
  }
  .login .code_box input:focus + label,
  .login .code_box input:hover + label,
  .login  .code_box input:valid + label{
    top: -20px;
    color: #03e9f4;
    font-size: 12px;
  }

  .login a{
    /*overflow: hidden;*/
    position: relative;
    padding: 10px 20px;
    color: #03e9f4;
    /*取消a表现原有的下划线*/
    text-decoration: none;
    /*同样加个过渡*/
    transition: all 0.5s;
  }
  .ant-btn{
    margin-left: 15px;
  }
</style>
