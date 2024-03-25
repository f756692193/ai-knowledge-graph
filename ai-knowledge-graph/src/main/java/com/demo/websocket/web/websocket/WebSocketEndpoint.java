package com.demo.websocket.web.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.demo.websocket.web.config.Cache;
import com.demo.websocket.web.util.AliYunUtil;
import com.demo.websocket.web.util.CallLimiter;
import com.demo.websocket.web.util.MD5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * serverEndpoint
 * */
@Component
@ServerEndpoint("/websocket")
@Slf4j
public class WebSocketEndpoint {
    private static CopyOnWriteArrayList<Session> sessions = new CopyOnWriteArrayList<>();
    private Thread heartbeatThread;
    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

//    @Autowired
    AliYunUtil aliYunUtil = new AliYunUtil();

    /**
     * 打开连接
     * */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        // 保存 session
        sessions.add(session);
        log.info("[websocket] new collection, id: {}", this.session.getId());
        // 开始心跳
//        startHeartbeat(session);
    }

    /**
     * 连接关闭
     * */
    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        log.info("[websocket] close collection, id: {}", this.session.getId());
        // 停止心跳
//        stopHeartbeat();
    }

    /**
     * 连接异常
     * */
    @OnError
    public void onError(Session session, Throwable throwable) throws IOException {
        log.info("[websocket] collection error, id: {}", this.session.getId(), throwable);
        // 关闭连接。状态码为 UNEXPECTED_CONDITION（意料之外的异常）
        this.session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, throwable.getMessage()));
    }

    /**
     * 收到消息
     * */
    @OnMessage
    public void onMessage(String msg, Session session) throws Exception {
        try {
            String clientIp = null;
            try {
                clientIp = getClientIp(session);
            } catch (Exception e) {
                log.error("[websocket] get client ip error, id: {}", session.getId(), e);
            }
            log.info("[websocket] receive message, id: {}, ip: {}, message: {}", session.getId(), clientIp, msg);
            JSONObject jsonObject = null;
            try {
                jsonObject = JSONObject.parseObject(msg);
            } catch (Exception e) {
                log.error("[websocket] parse message error, id: {}, message: {}", session.getId(), msg, e);
                return;
            }
            String ip = jsonObject.getString("ip");
            String message = jsonObject.getString("msg");
            if(message == null || message.trim().length() == 0) {
                return;
            }
            // 请求断开连接
            if("bye".equalsIgnoreCase(message)) {
                session.close();
                this.session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Bye"));
                sessions.remove(session);
                return;
            } else if("online_user".equals(message)) {
                // 返回在线用户
                Map<String, Object> map = new HashMap<>();
                map.put("online_user", sessions.size());
                session.getAsyncRemote().sendText(JSON.toJSONString(map));
                return;

            }
            // 简单的IP访问限制
            if (!CallLimiter.allowCall(ip)) {
                session.getAsyncRemote().sendText(buildResponse(null, null, "您今日的体验次数已达上限，请查看使用帮助"));
                return;
            }
            if(message == null || message.trim().length() == 0) {
                session.getAsyncRemote().sendText(buildResponse(null, null, "请输入描述内容"));
                return;
            }
            message = message.trim();
            // 长度判断
            if(message.length() > 200) {
                session.getAsyncRemote().sendText(buildResponse(null, null, "抱歉，您输入的文本超出了限制。"));
                return;
            }
            String key = MD5Util.string2MD5(message);
            if (Cache.graphs.get(key) != null) {
                String cache = Cache.graphs.get(key);
                // 重置 online_user
                JSONObject json = JSONObject.parseObject(cache);
                session.getAsyncRemote().sendText(buildResponse(json.getString("entity"), json.getString("relation"), json.getString("error")));
            } else {
                String prompt = "提取文本的实体和关系，构建知识谱图。只需返回实体和关系，使用开始实体、结束实体、开始关系、结束关系进行封装，返回格式如：<!--开始实体-->实体1;实体2;实体3<!--结束实体-->\n<!--开始关系-->实体1||来源于||实体2;实体2||属于||实体3<!--结束关系-->。\n需要提取的文本内容如下：\n" + message;

                // todo: 解析返回数据
//                 String result = aliYunUtil.makeApiCall(prompt);
                String result1 = "抱歉，根据您提供的文本内容，并没有直接明确的实体和关系可以构建知识图谱。该文本提到的是“学习广告投放的知识图谱”，但并没有给出具体的实体（如人名、地名、组织机构名或关键概念等）以及这些实体之间的关系。若要构建关于“广告投放”的知识图谱，可能需要更详细的内容，例如：\\n\\n实体[[广告投放课程; 广告平台; 目标受众; 竞价策略]]\\n关系[[广告投放课程-教授-广告平台使用方法; 广告投放-针对-目标受众; 广告投放-采用-竞价策略]]\\n\\n请提供更具体详细的文本内容以便进行精确的实体和关系抽取。";
                String result2 = "实体[[桑叶;生石膏;党参;南沙参;麦冬;杏仁;火麻仁;炙甘草;蜜紫菀;蜜枇杷叶;全栝楼;炒苏子;鸭梨皮]]\\n关系[[中药配方-包含-桑叶;中药配方-包含-生石膏;中药配方-包含-党参;中药配方-包含-南沙参;中药配方-包含-麦冬;中药配方-包含-杏仁;中药配方-包含-火麻仁;中药配方-包含-炙甘草;中药配方-包含-蜜紫菀;中药配方-包含-蜜枇杷叶;中药配方-包含-全栝楼;中药配方-包含-炒苏子;中药配方-包含-鸭梨皮]]";
                String result3 = "抱歉";
                String result4 = "实体[[胃流感（胃肠炎）;腹泻;细菌性胃肠炎;恶心;呕吐;肚子痛;抽筋;食欲不振;脱水;皮肤干燥;口干;头晕;口渴;幼儿;电解质失衡;死亡;疾病;胃肠炎;发热;头痛;全身酸痛;发冷;疲劳;流感病毒（流感）;流感]]\\n\\n关系[[胃流感（胃肠炎）-导致-腹泻;细菌性胃肠炎-可能导致-腹泻带血;胃流感（胃肠炎）-伴随症状-恶心;胃流感（胃肠炎）-伴随症状-呕吐;胃流感（胃肠炎）-持续症状-肚子痛;胃流感（胃肠炎）-持续症状-抽筋;胃流感（胃肠炎）-持续症状-食欲不振;胃流感（胃肠炎）-可能引发-脱水;脱水-表现为-皮肤干燥;脱水-表现为-口干;脱水-表现为-头晕;脱水-表现为-口渴;胃流感（胃肠炎）-对幼儿影响-电解质失衡;电解质失衡-可能导致-死亡;疾病-引起-胃肠炎;胃肠炎-伴随症状-发热;胃肠炎-伴随症状-头痛;胃肠炎-伴随症状-全身酸痛;胃肠炎-伴随症状-发冷;胃肠炎-伴随症状-疲劳;胃流感（胃肠炎）-与-流感病毒（流感）-混淆]]";
                String result5 = "实体(川贝止咳糖浆; 川贝母; 桔梗; 杏仁; 鲜生姜; 冰糖)\n关系(川贝止咳糖浆-包含-川贝母; 川贝止咳糖浆-包含-桔梗; 川贝止咳糖浆-包含-杏仁; 川贝止咳糖浆-包含-鲜生姜; 川贝止咳糖浆-包含-冰糖)";

                String result6 = "<!--开始实体-->胃流感（胃肠炎）;腹泻;细菌性胃肠炎;恶心;呕吐;肚子痛;抽筋;食欲不振;脱水;皮肤干燥;口干;头晕;口渴;幼儿;电解质失衡;死亡;疾病;发热;头痛;全身酸痛;发冷;疲劳;流感病毒（流感）<!--结束实体-->\\n<!--开始关系-->胃流感（胃肠炎）||出现症状||腹泻;细菌性胃肠炎||导致||腹泻带血;腹泻、呕吐||引起||肚子痛、抽筋、食欲不振;胃流感（胃肠炎）||可能引发||脱水;脱水||表现为||皮肤干燥、口干、头晕、口渴;腹泻和呕吐||导致||幼儿电解质失衡;电解质失衡||若不及时治疗||可能导致死亡;疾病||引起||胃肠炎;胃肠炎||伴随症状||发热、头痛、全身酸痛、发冷、疲劳;胃流感（胃肠炎）||与||流感病毒（流感）||存在混淆||由于症状重叠<!--结束关系-->";
                String result7 = "<!--开始实体-->川贝止咳糖浆;川贝母;桔梗;杏仁;鲜生姜;冰糖<!--结束实体-->\n<!--开始关系-->川贝止咳糖浆||包含成分||川贝母;川贝止咳糖浆||包含成分||桔梗;川贝止咳糖浆||包含成分||杏仁;川贝止咳糖浆||包含成分||鲜生姜;川贝止咳糖浆||包含成分||冰糖<!--结束关系-->";
                String result8 = "<!--开始实体-->张三;李四;王五;赵六;刘七;钱八;孙九;李兰;王芳<!--结束实体-->\n<!--开始关系-->张三||的父亲||李四;张三||的母亲||王五;李四||的父亲||赵六;李四||的母亲||刘七;王五||的父亲||钱八;王五||的母亲||孙九;张三||的大姨||李兰;李兰||的父亲||李四的哥哥;李兰||的母亲||李四的嫂子;张三||的姑姑||王芳;王芳||的父亲||王五的哥哥;王芳||的母亲||王五的嫂子;张三||的爷爷||赵六的爸爸;张三||的奶奶||刘七的妈妈;张三||的外公||钱八的爸爸;张三||的外婆||孙九的妈妈;张三||的表哥||李兰的儿子;张三||的表姐||王芳的女儿<!--结束关系-->";
                String result9 = "<!--开始实体-->数学知识;信息处理技术;语义网络;构建知识图谱的方法;应用场景<!--结束实体-->\n<!--开始关系-->数学知识||是基础||知识图谱;信息处理技术||用于处理||知识图谱的信息;语义网络||是核心技术之一||知识图谱;构建知识图谱的方法||包括||实体识别、关系抽取、知识推理;知识图谱||应用于||智能搜索、智能问答、个性化推荐、内容分发<!--结束关系-->";
                String result10 = "<!--开始实体-->爱新觉罗溥仪;爱新觉罗福临;爱新觉罗玄烨;爱新觉罗胤禛;爱新觉罗胤禩;崇庆皇贵妃;爱新觉罗·皇后;爱新觉罗·端妃;爱新觉罗·皇太极<!--结束实体-->\\n<!--开始关系-->爱新觉罗溥仪||父亲||爱新觉罗福临;爱新觉罗溥仪||兄弟||爱新觉罗玄烨;爱新觉罗溥仪||兄弟||爱新觉罗胤禛;爱新觉罗溥仪||兄弟||爱新觉罗胤禩;爱新觉罗溥仪||妈妈||崇庆皇贵妃;爱新觉罗溥仪||姐妹||爱新觉罗·皇后;爱新觉罗溥仪||姐妹||爱新觉罗·端妃;爱新觉罗溥仪||爷爷||爱新觉罗·皇太极;爱新觉罗玄烨||父亲||爱新觉罗福临;爱新觉罗玄烨||兄弟||爱新觉罗胤禛;爱新觉罗玄烨||兄弟||爱新觉罗胤禩;爱新觉罗玄烨||妈妈||孝庄文皇后;爱新觉罗玄烨||姐妹||爱新觉罗·和硕柔嘉公主;爱新觉罗玄烨||爷爷||爱新觉罗·皇太极;爱新觉罗胤禛||父亲||爱新觉罗福临;爱新觉罗胤禛||兄弟||爱新觉罗胤禩;爱新觉罗胤禛||妈妈||孝贞皇后;爱新觉罗胤禛||姐妹||爱新觉罗·荣康公主;爱新觉罗胤禛||爷爷||爱新觉罗福临;爱新觉罗胤禩||父亲||爱新觉罗福临;爱新觉罗胤禩||妈妈||孝贞皇后;爱新觉罗胤禩||姐妹||爱新觉罗·纯祺公主;爱新觉罗胤禩||爷爷||爱新觉罗胤禛<!--结束关系-->";
                String result = result10;
                if(result == null || result.trim().length() == 0) {
                    session.getAsyncRemote().sendText(buildResponse(null, null, "抱歉，请求过于火爆，触发预算阈值"));
                    return;
                }
                result = result.trim();
                String res;

                // 定义可能的字符串组合
                String[] group1 = {"<!--开始实体-->", "<!--结束实体-->", "<!--开始关系-->", "<!--结束关系-->"};
                if(containsStringGroup(result, group1)) {
                    // 提取数据中的实体和关系
                    String entity = result.substring(result.indexOf(group1[0]) + group1[0].length(), result.indexOf(group1[1]));
                    String relation = result.substring(result.indexOf(group1[2]) + group1[2].length(), result.lastIndexOf(group1[3]));

                    List<String> entityList = getEntityList(entity);
                    String buildEntity = buildEntity(entityList);
                    Map<String, Object> buildRelationMap = buildRelation(relation, entityList);
                    String buildRelation = (String) buildRelationMap.get("relation");
                    // set to list
                    if(buildRelationMap.containsKey("add_entity") && buildRelationMap.get("add_entity") != null) {
                        Set<String> addList = (Set<String>) buildRelationMap.get("add_entity");
                        if(addList != null && addList.size() > 0) {
                            // 合并 addList 和 entityList
                            entityList.addAll(addList);
                            buildEntity = buildEntity(entityList);
                        }
                    }
                    res = buildResponse(buildEntity, buildRelation, null);
                } else {
                    res = buildResponse(null, null, "抱歉，根据您提供的文本内容，并没有直接明确的实体和关系可以构建知识图谱。");
                }

                log.info("response: {}", res);
                Cache.graphs.put(key, res);

                session.getAsyncRemote().sendText(res);
            }
        } catch (Exception e) {
            log.error("[websocket] handle message error, id: {}, message: {}", session.getId(), msg, e);
        }
    }

    /**
     * 判断字符串是否包含指定的字符串组合
     * */
    public boolean containsStringGroup(String input, String[] group) {
        for (String str : group) {
            if (!input.contains(str)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构造 entity 对象
     * */
    public String buildEntity(List<String> entities) {
        if(entities == null || entities.size() == 0) {
            return null;
        }
        // 创建 json array
        JSONArray jsonArray = new JSONArray();
        for(String entity : entities) {
            entity = entity.trim();
            if(entity.length() == 0) {
                continue;
            }
            String name = entity;
            String desc = entity;
            int symbolSize = 50;
            int category = 0;
            if(entity.length() > 3 && entity.length() <= 5) {
                symbolSize = 70;
            } else if(entity.length() > 5) {
                symbolSize = 100;
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", name);
            jsonObject.put("des", desc);
            jsonObject.put("symbolSize", symbolSize);
            jsonObject.put("category", category);

            jsonArray.add(jsonObject);
        }
        return JSON.toJSONString(jsonArray);
    }

    /**
     * 获取实体对象
     * */
    public List<String> getEntityList(String entities) {
        if(entities == null || entities.trim().length() == 0) {
            return null;
        }
        String[] entityArray = entities.split(";");
        List<String> list = new ArrayList<>();
        for(String entity : entityArray) {
            entity = entity.trim();
            if(entity.length() == 0) {
                continue;
            }
            list.add(entity);
        }
        return list;
    }

    /**
     * 构造 relation 对象
     * */
    public Map<String, Object> buildRelation(String relations, List<String> entityList) {
        if (relations == null || relations.trim().length() == 0) {
            return null;
        }
        String[] relationArray = relations.split(";");
        // 创建 json array
        JSONArray jsonArray = new JSONArray();
        // entity 中缺少的实体, 需要补充
        Set<String> addList = new HashSet<>();
        for (String relation : relationArray) {
            relation = relation.trim();
            if (relation.length() == 0) {
                continue;
            }
            String[] array = relation.split("\\|\\|");
            if(array.length < 3) {
                continue;
            }
            String source = array[0];
            String name = array[1];
            String desc = array[1];
            String target = array[2];

            if(!entityList.contains(source)) {
                addList.add(source);
            }
            if(!entityList.contains(target)) {
                addList.add(target);
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("source", source);
            jsonObject.put("target", target);
            jsonObject.put("name", name);
            jsonObject.put("des", desc);
            jsonArray.add(jsonObject);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("relation", JSON.toJSONString(jsonArray));
        map.put("add_entity", addList);
        return map;
    }

    /**
     * 构造返回数据
     * */
    public String buildResponse(String entity, String relation, String error) {
        Map<String, Object> map = new HashMap<>();
        if(entity != null) {
            map.put("entity", entity);
        }
        if(relation != null) {
            map.put("relation", relation);
        }
        map.put("online_user", sessions.size());
        if (error != null) {
            map.put("error", error);
        }
        return JSON.toJSONString(map);
    }

    /**
     * 构造错误返回
     * */
    public String buildErrorResponse(String entity, String relation) {


        Map<String, Object> map = new HashMap<>();
        if(entity != null) {
            map.put("entity", entity);
        }
        if(relation != null) {
            map.put("relation", relation);
        }
        map.put("online_user", sessions.size());
        return JSON.toJSONString(map);
    }

    /**
     * 发送心跳消息
     * */
    private void sendHeartbeat(Session session) {
        try {
            session.getBasicRemote().sendText("heartbeat");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始心跳定时器
     * */
    private void startHeartbeat(Session session) {
        heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // 每5秒发送一次心跳
                    sendHeartbeat(session);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        });
        heartbeatThread.start();
    }

    /**
     * 停止心跳定时器
     * */
    private void stopHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }
    }

    /**
     * 发送信息方法
     * */
    private void sendMessage(Session session) {
        String message = "";
        //发送文本消息
        session.getAsyncRemote().sendText(message);
        Object obj = null;
        //发送对象消息，会尝试使用Encoder编码
        session.getAsyncRemote().sendObject(obj);
    }

    /**
     * 获取客户端IP
     * */
    public String getClientIp(Session session) {
        if (session == null) {
            return null;
        }
        RemoteEndpoint.Async async = session.getAsyncRemote();

        //在Tomcat 8.0.x版本有效
//		InetSocketAddress addr = (InetSocketAddress) getFieldInstance(async,"base#sos#socketWrapper#socket#sc#remoteAddress");
        //在Tomcat 8.5以上版本有效
        InetSocketAddress addr = (InetSocketAddress) getFieldInstance(async,"base#socketWrapper#socket#sc#remoteAddress");
        return addr.getHostString();
    }

    private Object getFieldInstance(Object obj, String fieldPath) {
        String fields[] = fieldPath.split("#");
        for (String field : fields) {
            obj = getField(obj, obj.getClass(), field);
            if (obj == null) {
                return null;
            }
        }
        return obj;
    }

    private Object getField(Object obj, Class<?> clazz, String fieldName) {
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                Field field;
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e) {
            }
        }
        return null;
    }
}


