//避免多个功能使用一个key，所以将所有session key 统一存放在sessionStorage中

SessionStorage ={
    set:function(key,value){
        // JSON.stringify()可以将json对象转化为字符串，可以更好的存储数据而不限制数据类型
            window.sessionStorage.setItem(key, JSON.stringify(value));
    },
    get:function(key){
        // JSON.parse()可以将字符串转化为json对象
        return JSON.parse(window.sessionStorage.getItem(key))
    },
    remove:function(key){
        window.sessionStorage.removeItem(key)
    },
    clear:function(){
        window.sessionStorage.clear()
    }
}