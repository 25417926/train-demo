//import com.guo.common.util.JWTUtil;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * @Description
// * @Author: 派
// * @Date: 2024/12/20
// */
//public class Test {
//    @org.junit.jupiter.api.Test
//    public void test() throws Exception{
//        Map<String, Object>map =new HashMap<>();
//        map.put("name","admin");
//        map.put("password","123456");
//        String token = JWTUtil.generateToken(map);
//        System.out.print(token);
//    }
//    @org.junit.jupiter.api.Test
//    public void test2(){
//        String token = "eyJhbGciOiJIUzI1NiJ9.eyJwYXNzd29yZCI6IjEyMzQ1NiIsIm5hbWUiOiJhZG1pbiIsImV4cCI6MTczNDY5MDIwNn0.NKBOc6ExZGpOIBxVOOnnubpFDwlEl7xjS5vEiHhrojI";
//        boolean flag = JWTUtil.isTokenExpired(token);
//        System.out.println(flag);
//
//
//    }
//}
