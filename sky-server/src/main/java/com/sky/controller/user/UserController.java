package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.properties.WeChatProperties;
import com.sky.result.Result;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import com.sky.websock.WebSocketServer;   // 新增导入
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserMapper userMapper;

    /**
     * 生成微信登录二维码的授权链接
     * 前端需传入当前 WebSocket 连接的 sid
     */
    @GetMapping("/wxLogin")
    public Result<String> wxLoginPage(@RequestParam("sid") String sid) throws Exception {
        // 1. 生成随机 state，并关联 sid
        String state = UUID.randomUUID().toString().replace("-", "");
        WebSocketServer.putState(state, sid);   // 存储临时映射

        // 2. 对回调地址进行 URL 编码
        //String redirectUrl = weChatProperties.getRedirectUrl();
        String redirectUrl = URLEncoder.encode(
                weChatProperties.getRedirectUrl(),
                "UTF-8");
        log.info("回调地址: {}", redirectUrl);

        // 3. 拼接微信 OAuth2.0 授权链接（带上动态 state）
        String url = "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + weChatProperties.getAppid()
                + "&redirect_uri=" + redirectUrl
                + "&response_type=code"
                + "&scope=snsapi_userinfo"
                + "&state=" + state         // 动态 state
                + "#wechat_redirect";


        log.info("微信登录二维码链接: {}", url);
        return Result.success(url);
    }

    /**
     * 微信回调（手机端访问）
     */
    @GetMapping("/wxCallback")
    public Result<String> wxCallback(@RequestParam String code,
                                     @RequestParam String state,
                                     HttpServletResponse response) {
        log.info("微信回调: code={}, state={}", code, state);

        // 1. 校验 state 是否有效，并取出 sid
        String sid = WebSocketServer.consumeState(state);
        if (sid == null) {
            log.warn("无效的 state: {}", state);
            throw new BaseException("登录会话已过期，请重新扫码");
        }

        try {
            // 2. 用 code 换取 access_token 和 openid
            String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token"
                    + "?appid=" + weChatProperties.getAppid()
                    + "&secret=" + weChatProperties.getSecret()
                    + "&code=" + code
                    + "&grant_type=authorization_code";

            String tokenResult = HttpClientUtil.doGet(tokenUrl, null);
            JSONObject tokenJson = JSON.parseObject(tokenResult);
            String openid = tokenJson.getString("openid");
            String accessToken = tokenJson.getString("access_token");

            // 检查微信接口是否返回错误
            if (openid == null || tokenJson.getInteger("errcode") != null) {
                log.error("微信获取 token 失败: {}", tokenResult);
                // 推送错误给 PC 端
                WebSocketServer.sendToClient(sid, "{\"type\":\"error\",\"message\":\"微信授权失败\"}");
               throw new BaseException("微信授权失败");
            }

            log.info("获取到 openid: {}", openid);

            // 3. 获取用户信息
            String userInfoUrl = "https://api.weixin.qq.com/sns/userinfo"
                    + "?access_token=" + URLEncoder.encode(accessToken, "UTF-8")
                    + "&openid=" + openid
                    + "&lang=zh_CN";
            String userInfoResult = HttpClientUtil.doGet(userInfoUrl, null);
            JSONObject userInfoJson = JSON.parseObject(userInfoResult);

            if (userInfoJson.getInteger("errcode") != null) {
                log.error("获取用户信息失败: {}", userInfoResult);
                WebSocketServer.sendToClient(sid, "{\"type\":\"error\",\"message\":\"获取用户信息失败\"}");
                throw new BaseException("获取用户信息失败");
            }

            // 4. 根据 openid 查询或创建用户
            User user = userMapper.getByOpenid(openid);
            if (user == null) {
                user = User.builder()
                        .openid(openid)
                        .name(userInfoJson.getString("nickname"))
                        .sex(userInfoJson.getString("sex"))
                        .avatar(userInfoJson.getString("headimgurl"))
                        .createTime(LocalDateTime.now())
                        .build();
                userMapper.insert(user);
            } else {
                // 更新用户信息
                user.setName(userInfoJson.getString("nickname"));
                user.setSex(userInfoJson.getString("sex"));
                user.setAvatar(userInfoJson.getString("headimgurl"));
                userMapper.update(user);
            }

            // 5. 生成 JWT 令牌
            Map<String, Object> claims = new HashMap<>();
            claims.put(JwtClaimsConstant.USER_ID, user.getId());
            String token = JwtUtil.createJWT(
                    jwtProperties.getUserSecretKey(),
                    jwtProperties.getUserTtl(),
                    claims);

            // 6. 通过 WebSocket 向 PC 端推送登录成功信息
            UserLoginVO loginVO = UserLoginVO.builder()
                    .id(user.getId())
                    .openid(openid)
                    .name(userInfoJson.getString("nickname"))
                    .avatar(userInfoJson.getString("headimgurl"))
                    .token(token)
                    .build();
            String msg = JSON.toJSONString(new HashMap<String, Object>() {{
                put("type", "loginSuccess");
                put("data", loginVO);
            }});
            WebSocketServer.sendToClient(sid, msg);
            log.info("已推送登录成功消息给 sid={}", sid);

            // 7. 返回给手机浏览器一个成功提示（或者重定向到某一页面）
            // 注意：这里不能只返回 JSON，手机端浏览器需要看到友好页面
            // 最佳做法是重定向到一个静态成功页面，或直接返回一段 HTML
            response.setContentType("text/html; charset=utf-8");
            response.getWriter().write("<h2>登录成功！请返回电脑端查看</h2>");
            return null;  // 手动写出内容后，不再走框架的 Result 序列化

        } catch (Exception e) {
            log.error("微信回调处理异常", e);
            WebSocketServer.sendToClient(sid, "{\"type\":\"error\",\"message\":\"服务器内部错误\"}");
            throw new BaseException("服务器异常");
        }
    }
    @GetMapping("/wxCheck")
    public String wxCheck(@RequestParam String signature,
                          @RequestParam String timestamp,
                          @RequestParam String nonce,
                          @RequestParam String echostr) {
        log.info("微信接口验证: signature={}, timestamp={}, nonce={}, echostr={}", 
                signature, timestamp, nonce, echostr);
        
        String token = "sdad";
        String[] arr = new String[]{token, timestamp, nonce};
        Arrays.sort(arr);
        
        StringBuilder content = new StringBuilder();
        for (String s : arr) {
            content.append(s);
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(content.toString().getBytes());
            String encrypted = byteToStr(digest);
            
            log.info("计算签名: {}, 收到签名: {}", encrypted, signature);
            
            if (encrypted.equals(signature)) {
                log.info("签名验证成功");
                return echostr;
            } else {
                log.warn("签名验证失败");
                return "";
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-1加密异常", e);
            return "";
        }
    }

    private String byteToStr(byte[] byteArray) {
        StringBuilder strDigest = new StringBuilder();
        for (byte b : byteArray) {
            strDigest.append(byteToHexStr(b));
        }
        return strDigest.toString();
    }

    private String byteToHexStr(byte mByte) {
        char[] Digit = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] tempArr = new char[2];
        tempArr[0] = Digit[(mByte >>> 4) & 0X0F];
        tempArr[1] = Digit[mByte & 0X0F];
        return new String(tempArr);
    }
    /**
     * 获取用户信息
     */
    @GetMapping("/getUserInfo")
    public Result<User> getUserInfo() {
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        return Result.success(user);
    }
    /**
     * 修改用户信息
     */
    @PutMapping
    public Result update(@RequestBody User user) {
        log.info("修改用户信息：{}", user);
        userMapper.updateUser(user);
        return Result.success();
    }

}