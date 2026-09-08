    DROP DATABASE IF EXISTS `nacos`;

    CREATE DATABASE  `nacos` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

    SET NAMES utf8mb4;
    SET FOREIGN_KEY_CHECKS = 0;

    USE `nacos`;

    /******************************************/
    /*   表名称 = config_info   */
    /******************************************/
    CREATE TABLE `config_info` (
      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) DEFAULT NULL COMMENT 'group_id',
      `content` longtext NOT NULL COMMENT 'content',
      `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      `src_user` text COMMENT 'source user',
      `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
      `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
      `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
      `c_desc` varchar(256) DEFAULT NULL COMMENT 'configuration description',
      `c_use` varchar(64) DEFAULT NULL COMMENT 'configuration usage',
      `effect` varchar(64) DEFAULT NULL COMMENT '配置生效的描述',
      `type` varchar(64) DEFAULT NULL COMMENT '配置的类型',
      `c_schema` text COMMENT '配置的模式',
      `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';

    insert into config_info(id, data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key) values
    (1,'application-dev.yml','DEFAULT_GROUP','spring:\n  autoconfigure:\n    exclude: com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure\n\n# feign 配置\nfeign:\n  sentinel:\n    enabled: true\n  okhttp:\n    enabled: true\n  httpclient:\n    enabled: false\n  client:\n    config:\n      default:\n        connectTimeout: 10000\n        readTimeout: 10000\n  compression:\n    request:\n      enabled: true\n      min-request-size: 8192\n    response:\n      enabled: true\n\n# 暴露监控端点\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: ''*''\n','7c15e6670696b957ea14279036f8023f','2020-05-20 12:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','通用配置','null','null','yaml','',''),
    (2,'ruoyi-gateway-dev.yml','DEFAULT_GROUP','# ry-gateway-dev.yml 变更后配置（2026-08-31 发布到 nacos）\n# 变更点：ruoyi-alert 路由 Path 追加 /api/maintenance-plans/**（维护计划模块上线）\n# 回滚依据：删除 Path 段尾部的 ",/api/maintenance-plans/**" 后整体覆盖发布即可\nspring:\n  data:\n    redis:\n      host: 8.145.53.117\n      port: 6379\n      password:\n  cloud:\n    gateway:\n      server:\n        webflux:\n          discovery:\n            locator:\n              lowerCaseServiceId: true\n              enabled: true\n          routes:\n            # 认证中心\n            - id: ruoyi-auth\n              uri: lb://ruoyi-auth\n              predicates:\n                - Path=/auth/**\n              filters:\n                # 验证码处理\n                - name: CacheRequestBody\n                  args:\n                    bodyClass: java.lang.String\n                - ValidateCodeFilter\n                - StripPrefix=1\n            # 代码生成\n            - id: ruoyi-gen\n              uri: lb://ruoyi-gen\n              predicates:\n                - Path=/code/**\n              filters:\n                - StripPrefix=1\n            # 定时任务\n            - id: ruoyi-job\n              uri: lb://ruoyi-job\n              predicates:\n                - Path=/schedule/**\n              filters:\n                - StripPrefix=1\n            # 系统模块\n            - id: ruoyi-system\n              uri: lb://ruoyi-system\n              predicates:\n                - Path=/system/**\n              filters:\n                - StripPrefix=1\n            # 文件服务\n            - id: ruoyi-file\n              uri: lb://ruoyi-file\n              predicates:\n                - Path=/file/**\n              filters:\n                - StripPrefix=1\n\n            # 设备模块(前端 /api/equipment/** 转发,StripPrefix 剥掉 /api 前缀,模块内部路径为 /equipment/**)\n            - id: ruoyi-equipment\n              uri: lb://ruoyi-equipment\n              predicates:\n                - Path=/api/equipment/**\n              filters:\n                - StripPrefix=1\n            # 告警模块(模块内部路径自带 /api 前缀,不做 StripPrefix)\n            - id: ruoyi-alert\n              uri: lb://ruoyi-alert\n              predicates:\n                - Path=/api/alert-events/**,/api/alert-rules/**,/api/predict/**,/api/work-orders/**,/api/maintenance-plans/**\n# 安全配置\nsecurity:\n  # 验证码\n  captcha:\n    enabled: true\n    type: math\n  # 防止XSS攻击\n  xss:\n    enabled: true\n    excludeUrls:\n      - /system/notice\n\n  # 不校验白名单\n  ignore:\n    whites:\n      - /auth/logout\n      - /auth/login\n      - /auth/register\n      - /*/v2/api-docs\n      - /*/v3/api-docs\n      - /csrf\n\n# springdoc配置\nspringdoc:\n  webjars:\n    # 访问前缀\n    prefix:\n','4aec083de4578ffe07246fccde43676f','2020-05-20 12:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','网关模块','null','null','yaml','',''),
    (3,'ruoyi-auth-dev.yml','DEFAULT_GROUP','spring:\n  data:\n    redis:\n      host: 8.145.53.117\n      port: 6379\n      password: \n','52a114cc4d5589163c769bba6025a80f','2020-11-20 00:00:00','2024-09-14 04:49:42','nacos','0:0:0:0:0:0:0:1','','','认证中心','null','null','yaml','',''),
    (4,'ruoyi-monitor-dev.yml','DEFAULT_GROUP','# spring\nspring:\n  security:\n    user:\n      name: ruoyi\n      password: 123456\n  boot:\n    admin:\n      ui:\n        title: 若依服务状态监控\n','6f122fd2bfb8d45f858e7d6529a9cd44','2020-11-20 00:00:00','2024-08-29 12:15:11','nacos','0:0:0:0:0:0:0:1','','','监控中心','null','null','yaml','',''),
    (5,'ruoyi-system-dev.yml','DEFAULT_GROUP','# spring配置\nspring:\n  data:\n    redis:\n      host: 8.145.53.117\n      port: 6379\n      password: \n  datasource:\n    druid:\n      stat-view-servlet:\n        enabled: true\n        loginUsername: ruoyi\n        loginPassword: 123456\n    dynamic:\n      druid:\n        initial-size: 5\n        min-idle: 5\n        maxActive: 20\n        maxWait: 60000\n        connectTimeout: 30000\n        socketTimeout: 60000\n        timeBetweenEvictionRunsMillis: 60000\n        minEvictableIdleTimeMillis: 300000\n        validationQuery: SELECT 1 FROM DUAL\n        testWhileIdle: true\n        testOnBorrow: false\n        testOnReturn: false\n        poolPreparedStatements: true\n        maxPoolPreparedStatementPerConnectionSize: 20\n        filters: stat,slf4j\n        connectionProperties: druid.stat.mergeSql\\=true;druid.stat.slowSqlMillis\\=5000\n      datasource:\n          # 主库数据源\n          master:\n            driver-class-name: com.mysql.cj.jdbc.Driver\n            url: jdbc:mysql://8.145.53.117:3306/ymzw2?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8\n            username: root\n            password: 123!@#QWer\n          # 从库数据源\n          # slave:\n            # username: \n            # password: \n            # url: \n            # driver-class-name: \n\n# mybatis配置\nmybatis:\n    # 搜索指定包别名\n    typeAliasesPackage: com.ruoyi.system\n    # 配置mapper的扫描，找到所有的mapper.xml映射文件\n    mapperLocations: classpath:mapper/**/*.xml\n\n# springdoc配置\nspringdoc:\n  gatewayUrl: http://localhost:8080/${spring.application.name}\n  api-docs:\n    # 是否开启接口文档\n    enabled: true\n  info:\n    # 标题\n    title: ''系统模块接口文档''\n    # 描述\n    description: ''系统模块接口描述''\n    # 作者信息\n    contact:\n      name: RuoYi\n      url: https://ruoyi.vip\n','7fb3c8081dccb1f3d62d8dc25826ab55','2020-05-20 12:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','系统模块','null','null','yaml','',''),
    (6,'ruoyi-gen-dev.yml','DEFAULT_GROUP','# spring配置\nspring:\n  data:\n    redis:\n      host: 8.145.53.117\n      port: 6379\n      password: \n  datasource:\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    url: jdbc:mysql://8.145.53.117:3306/ry-cloud?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8\n    username: root\n    password: password\n\n# mybatis配置\nmybatis:\n    # 搜索指定包别名\n    typeAliasesPackage: com.ruoyi.gen.domain\n    # 配置mapper的扫描，找到所有的mapper.xml映射文件\n    mapperLocations: classpath:mapper/**/*.xml\n\n# springdoc配置\nspringdoc:\n  gatewayUrl: http://localhost:8080/${spring.application.name}\n  api-docs:\n    # 是否开启接口文档\n    enabled: true\n  info:\n    # 标题\n    title: \'代码生成接口文档\'\n    # 描述\n    description: \'代码生成接口描述\'\n    # 作者信息\n    contact:\n      name: RuoYi\n      url: https://ruoyi.vip\n\n# 代码生成\ngen:\n  # 作者\n  author: ruoyi\n  # 默认生成包路径 system 需改成自己的模块名称 如 system monitor tool\n  packageName: com.ruoyi.system\n  # 自动去除表前缀，默认是false\n  autoRemovePre: false\n  # 表前缀（生成类名不会包含表前缀，多个用逗号分隔）\n  tablePrefix: sys_\n  # 是否允许生成文件覆盖到本地（自定义路径），默认不允许\n  allowOverwrite: false','4d00884aef610e75b26865e9654f07c0','2020-11-20 00:00:00','2024-12-25 08:39:25','nacos','0:0:0:0:0:0:0:1','','','代码生成','null','null','yaml','',''),
    (7,'ruoyi-job-dev.yml','DEFAULT_GROUP','# spring配置\nspring:\n  data:\n    redis:\n      host: 8.145.53.117\n      port: 6379\n      password: \n  datasource:\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    url: jdbc:mysql://8.145.53.117:3306/ry-cloud?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8\n    username: root\n    password: password\n\n# mybatis配置\nmybatis:\n    # 搜索指定包别名\n    typeAliasesPackage: com.ruoyi.job.domain\n    # 配置mapper的扫描，找到所有的mapper.xml映射文件\n    mapperLocations: classpath:mapper/**/*.xml\n\n# springdoc配置\nspringdoc:\n  gatewayUrl: http://localhost:8080/${spring.application.name}\n  api-docs:\n    # 是否开启接口文档\n    enabled: true\n  info:\n    # 标题\n    title: \'定时任务接口文档\'\n    # 描述\n    description: \'定时任务接口描述\'\n    # 作者信息\n    contact:\n      name: RuoYi\n      url: https://ruoyi.vip\n','0ea1b408dc63fe9c9b0830634e301aaf','2020-11-20 00:00:00','2024-09-14 04:50:12','nacos','0:0:0:0:0:0:0:1','','','定时任务','null','null','yaml','',''),
    (8,'ruoyi-file-dev.yml','DEFAULT_GROUP','# 本地文件上传    \nfile:\n    domain: http://127.0.0.1:9300\n    path: D:/ruoyi/uploadPath\n    prefix: /statics\n\n# FastDFS配置\nfdfs:\n  domain: http://127.0.0.1\n  soTimeout: 3000\n  connectTimeout: 2000\n  trackerList: 127.0.0.1:22122\n\n# Minio配置\nminio:\n  url: http://127.0.0.1:9000\n  accessKey: minioadmin\n  secretKey: minioadmin\n  bucketName: test\n\n  # 防盗链配置\nreferer:\n  # 防盗链开关\n  enabled: false\n  # 允许的域名列表\n  allowed-domains: localhost,127.0.0.1,ruoyi.vip,www.ruoyi.vip\n','7c2b947798cbb9e3bd47412762aefd88','2020-05-20 12:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','文件服务','null','null','yaml','',''),
    (9,'sentinel-ruoyi-gateway','DEFAULT_GROUP','[\r\n    {\r\n        \"resource\": \"ruoyi-auth\",\r\n        \"count\": 500,\r\n        \"grade\": 1,\r\n        \"limitApp\": \"default\",\r\n        \"strategy\": 0,\r\n        \"controlBehavior\": 0\r\n    },\r\n	{\r\n        \"resource\": \"ruoyi-system\",\r\n        \"count\": 1000,\r\n        \"grade\": 1,\r\n        \"limitApp\": \"default\",\r\n        \"strategy\": 0,\r\n        \"controlBehavior\": 0\r\n    },\r\n	{\r\n        \"resource\": \"ruoyi-gen\",\r\n        \"count\": 200,\r\n        \"grade\": 1,\r\n        \"limitApp\": \"default\",\r\n        \"strategy\": 0,\r\n        \"controlBehavior\": 0\r\n    },\r\n	{\r\n        \"resource\": \"ruoyi-job\",\r\n        \"count\": 300,\r\n        \"grade\": 1,\r\n        \"limitApp\": \"default\",\r\n        \"strategy\": 0,\r\n        \"controlBehavior\": 0\r\n    }\r\n]','9f3a3069261598f74220bc47958ec252','2020-11-20 00:00:00','2020-11-20 00:00:00',NULL,'0:0:0:0:0:0:0:1','','','限流策略','null','null','json',NULL,'');

insert into config_info(id, data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key) values
    (10,'ruoyi-equipment-dev.yml','DEFAULT_GROUP','spring:\n  # 数据源配置\n  # Druid 连接池(prefix: spring.datasource.druid,与 RuoYi-Cloud 标准对齐)\n  datasource:\n    druid:\n      driver-class-name: com.mysql.cj.jdbc.Driver\n      url: jdbc:mysql://8.145.53.117:3306/ymzw2?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\n      username: root\n      password: 123!@#QWer\n      initial-size: 5\n      min-idle: 5\n      max-active: 20\n      max-wait: 60000\n      connect-timeout: 30000\n      socket-timeout: 60000\n      time-between-eviction-runs-millis: 60000\n      min-evictable-idle-time-millis: 300000\n      validation-query: SELECT 1 FROM DUAL\n      test-while-idle: true\n      test-on-borrow: false\n      test-on-return: false\n      pool-prepared-statements: true\n      max-pool-prepared-statement-per-connection-size: 20\n      filters: stat,slf4j\n      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000\n    # 次数据源 TDengine(REST JDBC 6041 + Druid,Windows 免 taos.dll)\n    # 由 TdengineConfig 通过 @ConfigurationProperties(prefix="spring.datasource.tdengine") 绑定\n    tdengine:\n      driver-class-name: com.taosdata.jdbc.rs.RestfulDriver\n      url: jdbc:TAOS-RS://${TDENGINE_HOST:8.145.53.117}:${TDENGINE_PORT:6041}/ymzw\n      username: ${TDENGINE_USERNAME:root}\n      password: ${TDENGINE_PASSWORD:taosdata}\n      initial-size: 2\n      min-idle: 2\n      max-active: 10\n      max-wait: 30000\n      validation-query: select server_version()\n      test-while-idle: true\n      test-on-borrow: false\n      test-on-return: false\n      # TDengine 原生长连接,不宜频繁回收。注意:Druid 强制 time-between-eviction-runs-millis > 0(设 0 启动即失败);\n      # "少回收"应增大 min-evictable-idle-time-millis,设 0 反而是"空闲即驱逐",每次驱逐线程都会清空空闲池\n      time-between-eviction-runs-millis: 60000\n      min-evictable-idle-time-millis: 3600000\n      pool-prepared-statements: false\n\n  mail:\n    host: smtp.qq.com\n    port: 587\n    username: 1521670478@qq.com\n    password: bmtiafvhndwuhiei\n    properties:\n      mail:\n        smtp:\n          auth: true\n          starttls:\n            enable: true\n            required: true\n# 告警邮件配置\nalert:\n  email:\n    to: wzh_game@outlook.com\n\n# ============ RocketMQ(传感器数据转发告警模块) ============\n# broker 未部署阶段:producer 启动仅告警不阻断,发送失败由 mqExecutor 线程记日志,部署后自动恢复\nrocketmq:\n  name-server: ${ROCKETMQ_NAMESRV:8.145.53.117:9876}\n  producer:\n    group: equipment-sensor-producer\n    send-message-timeout: 3000\n\n# MyBatis-Plus配置\nmybatis-plus:\n  mapper-locations: classpath:mapper/*.xml\n  type-aliases-package: com.ruoyi.equipment.entity\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n      logic-delete-field: deleteFlag\n      logic-delete-value: 1\n      logic-not-delete-value: 0\n\n# MQTT配置（设备消息接入）\nmqtt:\n  broker: tcp://${MQTT_BROKER:8.145.53.117}:1883\n  client-id: ${MQTT_CLIENT_ID:ruoyi-equipment}\n  username: ${MQTT_USERNAME:admin}\n  password: ${MQTT_PASSWORD:admin123}\n  topic:\n    sensor-data: iot/equipment/+/sensor/+/data\n    alert: iot/alert/+\n  qos: 1\n','b6d9db8b55404fc71a756a909741af13','2026-09-07 08:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','设备模块','null','null','yaml','',''),
    (11,'ruoyi-alert-dev.yml','DEFAULT_GROUP','# ============ spring 配置 ============\nspring:\n  # 主数据源 MySQL(告警规则/事件等关系数据,由 MysqlDataSourceConfig 声明 @Primary)\n  # Druid 连接池(prefix: spring.datasource.druid,与 RuoYi-Cloud 标准对齐)\n  # 注:TDengine 时序落库职责已移交 equipment 模块(传感器数据统一由其 MQTT 入口处理)\n  datasource:\n    druid:\n      driver-class-name: com.mysql.cj.jdbc.Driver\n      url: jdbc:mysql://${MYSQL_HOST:8.145.53.117}:${MYSQL_PORT:3306}/ymzw2?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true\n      username: ${MYSQL_USERNAME:root}\n      password: ${MYSQL_PASSWORD:123!@#QWer}\n      initial-size: 5\n      min-idle: 5\n      max-active: 20\n      max-wait: 60000\n      connect-timeout: 30000\n      socket-timeout: 60000\n      time-between-eviction-runs-millis: 60000\n      min-evictable-idle-time-millis: 300000\n      validation-query: SELECT 1 FROM DUAL\n      test-while-idle: true\n      test-on-borrow: false\n      test-on-return: false\n      pool-prepared-statements: true\n      max-pool-prepared-statement-per-connection-size: 20\n      filters: stat,slf4j\n      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000\n\n  # Redis(ruoyi-common-redis 传递引入)\n  data:\n    redis:\n      host: ${REDIS_HOST:8.145.53.117}\n      port: ${REDIS_PORT:6379}\n      password: ${REDIS_PASSWORD:}\n      database: 0\n      timeout: 10000ms\n\n  # 告警邮件推送(SMTP):host 为空时邮件推送自动降级跳过;\n  # 填入真实发件服务器/账号/授权码即真实发送\n  # 例(QQ 邮箱): host=smtp.qq.com port=465 username=发件邮箱 password=SMTP授权码(非登录密码)\n  mail:\n    host: ${MAIL_HOST:smtp.qq.com}\n    # 465=SMTPS(隐式SSL,需 ssl.enable=true);587=STARTTLS(明文连接后升级,需 starttls.enable)\n    port: ${MAIL_PORT:465}\n    username: ${MAIL_USERNAME:1521670478@qq.com}\n    password: ${MAIL_PASSWORD:bmtiafvhndwuhiei}\n    properties:\n      mail:\n        smtp:\n          auth: true\n          ssl:\n            enable: true\n\n# ============ MyBatis-Plus 配置(MySQL 侧,MP 3.5.17 SB4 starter) ============\nmybatis-plus:\n  mapper-locations: classpath:mapper/*.xml\n  type-aliases-package: com.ruoyi.alert.entity\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n      logic-delete-field: deleteFlag\n      logic-delete-value: 1\n      logic-not-delete-value: 0\n\n# ============ RocketMQ(消费 equipment 模块转发的传感器数据) ============\n# broker 未部署阶段:consumer 启动仅告警不阻断,后台持续重连,部署后自动恢复消费\nrocketmq:\n  name-server: ${ROCKETMQ_NAMESRV:8.145.53.117:9876}\n\n# ============ 告警通知(邮件+电话外呼) ============\nalert:\n  notify:\n    # 同设备+传感器+级别的通知冷却窗口(毫秒),防模拟器 20 秒/条告警的邮件电话风暴\n    throttle-ms: ${ALERT_THROTTLE_MS:600000}\n    # 阿里云语音外呼(SingleCallByTts):AK/模板任一为空时自动降级为"模拟外呼"日志\n    # 填入真实 RAM AccessKey 与控制台已报备的语音模板后即真实拨号\n    voice:\n      access-key-id: ${VOICE_AK_ID:}\n      access-key-secret: ${VOICE_AK_SECRET:}\n      template-code: ${VOICE_TEMPLATE_CODE:}\n      called-show-number: ${VOICE_SHOW_NUMBER:}\n      endpoint: dyvmsapi.aliyuncs.com\n\n# ============ 预测性维护(predict,B4 起模型推理) ============\npredict:\n  # 总开关:演示/联调开启\n  # (PredictTask 每轮调度开头检查,关闭时空转直接返回,不拉数据不落库)\n  enabled: false\n  # 预测任务调度间隔(毫秒,@Scheduled fixedDelay,上轮结束后起算)\n  # 注:历史窗口已由 ruoyi-ai 拉取(T2 数据获取上收),本模块无窗口点数配置\n  interval-ms: 30000\n  # ---- 劣化状态机 ----\n  # 入态连续异常轮数:isAnomaly 连续 true 达到该轮数才入态 DEGRADING\n  # (替代原 L2 突变单轮触发,防模型单轮毛刺误报)\n  anomaly-rounds: 2\n  # RUL 推后退出阈值(分钟):DEGRADING 态 RUL 较上轮推后超过该值视为劣化放缓,\n  # 幽灵退出回 NORMAL(防长期挂一条不兑现的预测)\n  rul-defer-exit-minutes: 60\n  # ---- 模型推理(经 Feign 调 ruoyi-ai 代理的 pdm-server) ----\n  model:\n    # 预测步长(分钟):单次推理向前外推时长,与 RUL(rulPoint 等)分钟口径一致\n    horizon: 48\n\n# ============ 维保工单(workorder) ============\nworkorder:\n  # 工单生成总开关:RULE/PREDICT 的 WARNING/SEVERE 告警自动转维保工单\n  # (WorkOrderCreateListener 消费 AlertTriggeredEvent,关闭后告警只通知不建单)\n  enabled: true\n  # 完成工单时联动设备退化复位:Feign resetDegradation → equipment 模块\n  # → MQTT maintenance/{equipmentNo} → 模拟器按设备清零全部退化数据\n  # (关闭后完成仅落工单状态,不下发复位指令)\n  reset-on-complete: true\n\n# ============ 维护计划(plan) ============\nplan:\n  # 定时扫描开关:关闭时任务空转,不生成新工单(既有计划与工单不受影响)\n  enabled: true\n  holiday:\n    # 节假日数据自动同步开关:关闭时不再拉取,已有缓存继续生效\n    sync-enabled: true\n    # 同步时刻(每日,拉取当年+次年防跨年空窗)\n    sync-cron: "0 10 3 * * ?"\n    connect-timeout-ms: 3000\n    read-timeout-ms: 5000\n\n# ============ springdoc 配置 ============\nspringdoc:\n  gatewayUrl: http://localhost:8080/${spring.application.name}\n  api-docs:\n    enabled: true\n  info:\n    title: ''告警模块接口文档''\n    description: ''云眸智维告警模块（规则管理 + RocketMQ 消费 + 事件驱动告警检测）''\n    contact:\n      name: ruoyi\n      url: https://ruoyi.vip\n\n# ============ 日志 ============\nlogging:\n  level:\n    com.ruoyi.alert: debug\n    com.alibaba.nacos: WARN\n    com.alibaba.cloud.nacos: WARN\n','147ae43b8b120a0dfab3838f6f157f12','2026-09-07 08:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','告警模块','null','null','yaml','',''),
    (12,'ruoyi-ai-dev.yml','DEFAULT_GROUP','spring:\n  # 数据源配置（业务模块自持，不依赖 Nacos 共享的 Druid 配置）\n  # Druid 连接池(prefix: spring.datasource.druid,与 RuoYi-Cloud 标准对齐)\n  datasource:\n    druid:\n      driver-class-name: com.mysql.cj.jdbc.Driver\n      url: jdbc:mysql://${MYSQL_HOST:8.145.53.117}:${MYSQL_PORT:3306}/ymzw2?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true\n      username: ${MYSQL_USERNAME:root}\n      password: ${MYSQL_PASSWORD:123!@#QWer}\n      initial-size: 5\n      min-idle: 5\n      max-active: 20\n      max-wait: 60000\n      connect-timeout: 30000\n      socket-timeout: 60000\n      time-between-eviction-runs-millis: 60000\n      min-evictable-idle-time-millis: 300000\n      validation-query: SELECT 1 FROM DUAL\n      test-while-idle: true\n      test-on-borrow: false\n      test-on-return: false\n      pool-prepared-statements: true\n      max-pool-prepared-statement-per-connection-size: 20\n      filters: stat,slf4j\n      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000\n\n  # AI配置（通过 Spring AI OpenAI 兼容接口）\n  # 当前使用：阿里云百练平台（DashScope）\n  # 如需切换 DeepSeek，将 base-url 改为 https://api.deepseek.com 并调整 model\n  ai:\n    openai:\n      base-url: https://dashscope.aliyuncs.com/compatible-mode\n      api-key: ${AI_API_KEY:sk-dde4aa6baf9844b38c8c9e7c555c654f}\n      chat:\n        options:\n          model: qwen-plus\n      embedding:\n        options:\n          model: text-embedding-v3\n\n    # Chroma 向量库（诊断智能体 RAG 检索；KnowledgeLoader 启动时装载 resources/knowledge 语料）\n    vectorstore:\n      chroma:\n        client:\n          host: http://localhost\n          port: 8000\n        initialize-schema: true\n        collection-name: pdm_knowledge\n\n# =====================================================================\n# B7: Ollama 本地大模型备份配置（现场无外网时使用）\n# ---------------------------------------------------------------------\n# 使用方法：比赛现场无法访问 DashScope 云端 API 时，先在本机安装并启动 Ollama\n# （默认端口 11434），执行 ollama pull qwen2.5:7b 拉取模型，\n# 然后将下方备份段取消注释、同时注释上方云端 spring.ai.openai 段即可切换。\n# 注意：嵌入模型 text-embedding-v3 无法本地化（Ollama 需另配专用嵌入模型），\n# 无外网场景下诊断 RAG 检索依赖 Chroma 中已装载的知识向量（有外网时装载），\n# 切换只影响对话/诊断报告的生成模型。\n# =====================================================================\n#  ai:\n#    openai:\n#      # Ollama 提供 OpenAI 兼容接口，Spring AI OpenAI starter 可直接对接\n#      base-url: http://localhost:11434\n#      # Ollama 不校验密钥，占位值即可\n#      api-key: ollama\n#      chat:\n#        options:\n#          model: qwen2.5:7b\n\n# MyBatis-Plus配置\nmybatis-plus:\n  mapper-locations: classpath:mapper/*.xml\n  type-aliases-package: com.ruoyi.ai.entity\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n      logic-delete-field: deleteFlag\n      logic-delete-value: 1\n      logic-not-delete-value: 0\n\n# pdm-server 推理服务地址（预测性维护独立推理进程,ruoyi-ai 仅做代理转发,不加载模型）\n# 超时约定:连接 3s / 读取 10s,硬编码在 PdmServerClient,防止推理慢请求挂死调用线程\npredict-server:\n  url: ${PREDICT_SERVER_URL:http://localhost:8900}\n\n# 日志配置\nlogging:\n  level:\n    com.ruoyi.ai: debug\n','7b9f3d6c98c66b2b6a2f2423b463be23','2026-09-07 08:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','AI模块','null','null','yaml','',''),
    (13,'ruoyi-inspection-dev.yml','DEFAULT_GROUP','spring:\n  # Druid 连接池(prefix: spring.datasource.druid,与 RuoYi-Cloud 标准对齐)\n  datasource:\n    druid:\n      driver-class-name: com.mysql.cj.jdbc.Driver\n      url: jdbc:mysql://${MYSQL_HOST:8.145.53.117}:${MYSQL_PORT:3306}/ry-cloud?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\n      username: ${MYSQL_USERNAME:root}\n      password: ${MYSQL_PASSWORD:123!@#QWer}\n      initial-size: 5\n      min-idle: 5\n      max-active: 20\n      max-wait: 60000\n      connect-timeout: 30000\n      socket-timeout: 60000\n      time-between-eviction-runs-millis: 60000\n      min-evictable-idle-time-millis: 300000\n      validation-query: SELECT 1 FROM DUAL\n      test-while-idle: true\n      test-on-borrow: false\n      test-on-return: false\n      pool-prepared-statements: true\n      max-pool-prepared-statement-per-connection-size: 20\n      filters: stat,slf4j\n      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000\n\nmybatis-plus:\n  mapper-locations: classpath:mapper/*.xml\n  type-aliases-package: com.ruoyi.inspection.entity\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n      logic-delete-field: deleteFlag\n      logic-delete-value: 1\n      logic-not-delete-value: 0\n\nlogging:\n  level:\n    com.ruoyi.inspection: debug\n\n# Chroma 向量库（HTTP REST 直连，非 Spring AI starter）\nchroma:\n  host: ${CHROMA_HOST:http://106.53.26.28}\n  port: ${CHROMA_PORT:8000}\n  tenant-name: default_tenant\n  database-name: default_database\n  collection-name: ${CHROMA_COLLECTION:ai_knowledge_base}\n  initialize-schema: false\n','5fc3d5b6c74000610318fa0aa734c609','2026-09-07 08:00:00','2026-09-07 08:00:00','nacos','127.0.0.1','','','巡检模块','null','null','yaml','','');


    /******************************************/
    /*   表名称 = config_info_aggr   */
    /******************************************/
    CREATE TABLE `config_info_aggr` (
      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(255) NOT NULL COMMENT 'group_id',
      `datum_id` varchar(255) NOT NULL COMMENT 'datum_id',
      `content` longtext NOT NULL COMMENT '内容',
      `gmt_modified` datetime NOT NULL COMMENT '修改时间',
      `app_name` varchar(128) DEFAULT NULL,
      `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`,`group_id`,`tenant_id`,`datum_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='增加租户字段';


    /******************************************/
    /*   表名称 = config_info  since 2.5.0    */
    /******************************************/
    CREATE TABLE `config_info_gray` (
      `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) NOT NULL COMMENT 'group_id',
      `content` longtext NOT NULL COMMENT 'content',
      `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
      `src_user` text COMMENT 'src_user',
      `src_ip` varchar(100) DEFAULT NULL COMMENT 'src_ip',
      `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_create',
      `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_modified',
      `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
      `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
      `gray_name` varchar(128) NOT NULL COMMENT 'gray_name',
      `gray_rule` text NOT NULL COMMENT 'gray_rule',
      `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_configinfogray_datagrouptenantgray` (`data_id`,`group_id`,`tenant_id`,`gray_name`),
      KEY `idx_dataid_gmt_modified` (`data_id`,`gmt_modified`),
      KEY `idx_gmt_modified` (`gmt_modified`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='config_info_gray';


    /******************************************/
    /*   表名称 = config_info_beta   */
    /******************************************/
    CREATE TABLE `config_info_beta` (
      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) NOT NULL COMMENT 'group_id',
      `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
      `content` longtext NOT NULL COMMENT 'content',
      `beta_ips` varchar(1024) DEFAULT NULL COMMENT 'betaIps',
      `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      `src_user` text COMMENT 'source user',
      `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
      `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
      `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_beta';

    /******************************************/
    /*   表名称 = config_info_tag   */
    /******************************************/
    CREATE TABLE `config_info_tag` (
      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) NOT NULL COMMENT 'group_id',
      `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
      `tag_id` varchar(128) NOT NULL COMMENT 'tag_id',
      `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
      `content` longtext NOT NULL COMMENT 'content',
      `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      `src_user` text COMMENT 'source user',
      `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`,`group_id`,`tenant_id`,`tag_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_tag';

    /******************************************/
    /*   表名称 = config_tags_relation   */
    /******************************************/
    CREATE TABLE `config_tags_relation` (
      `id` bigint(20) NOT NULL COMMENT 'id',
      `tag_name` varchar(128) NOT NULL COMMENT 'tag_name',
      `tag_type` varchar(64) DEFAULT NULL COMMENT 'tag_type',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) NOT NULL COMMENT 'group_id',
      `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
      `nid` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增长标识',
      PRIMARY KEY (`nid`),
      UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
      KEY `idx_tenant_id` (`tenant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_tag_relation';

    /******************************************/
    /*   表名称 = group_capacity   */
    /******************************************/
    CREATE TABLE `group_capacity` (
      `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
      `group_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
      `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
      `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
      `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
      `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，，0表示使用默认值',
      `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
      `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_group_id` (`group_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='集群、各Group容量信息表';

    /******************************************/
    /*   表名称 = his_config_info   */
    /******************************************/
    CREATE TABLE `his_config_info` (
      `id` bigint(20) unsigned NOT NULL COMMENT 'id',
      `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增标识',
      `data_id` varchar(255) NOT NULL COMMENT 'data_id',
      `group_id` varchar(128) NOT NULL COMMENT 'group_id',
      `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
      `content` longtext NOT NULL COMMENT 'content',
      `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      `src_user` text COMMENT 'source user',
      `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
      `op_type` char(10) DEFAULT NULL COMMENT 'operation type',
      `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
      `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
      `publish_type` varchar(50)  DEFAULT 'formal' COMMENT 'publish type gray or formal',
      `gray_name` varchar(50)  DEFAULT NULL COMMENT 'gray name',
      `ext_info`  longtext DEFAULT NULL COMMENT 'ext info',
      PRIMARY KEY (`nid`),
      KEY `idx_gmt_create` (`gmt_create`),
      KEY `idx_gmt_modified` (`gmt_modified`),
      KEY `idx_did` (`data_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='多租户改造';


    /******************************************/
    /*   数据库全名 = nacos_config   */
    /*   表名称 = tenant_capacity   */
    /******************************************/
    CREATE TABLE `tenant_capacity` (
      `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
      `tenant_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
      `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
      `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
      `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
      `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
      `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
      `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
      `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_tenant_id` (`tenant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='租户容量信息表';


    CREATE TABLE `tenant_info` (
      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
      `kp` varchar(128) NOT NULL COMMENT 'kp',
      `tenant_id` varchar(128) default '' COMMENT 'tenant_id',
      `tenant_name` varchar(128) default '' COMMENT 'tenant_name',
      `tenant_desc` varchar(256) DEFAULT NULL COMMENT 'tenant_desc',
      `create_source` varchar(32) DEFAULT NULL COMMENT 'create_source',
      `gmt_create` bigint(20) NOT NULL COMMENT '创建时间',
      `gmt_modified` bigint(20) NOT NULL COMMENT '修改时间',
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
      KEY `idx_tenant_id` (`tenant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='tenant_info';

    CREATE TABLE `users` (
      `username` varchar(50) NOT NULL PRIMARY KEY COMMENT 'username',
      `password` varchar(500) NOT NULL COMMENT 'password',
      `enabled` boolean NOT NULL COMMENT 'enabled'
    );

    CREATE TABLE `roles` (
      `username` varchar(50) NOT NULL COMMENT 'username',
      `role` varchar(50) NOT NULL COMMENT 'role',
      UNIQUE INDEX `idx_user_role` (`username` ASC, `role` ASC) USING BTREE
    );

    CREATE TABLE `permissions` (
      `role` varchar(50) NOT NULL COMMENT 'role',
      `resource` varchar(128) NOT NULL COMMENT 'resource',
      `action` varchar(8) NOT NULL COMMENT 'action',
      UNIQUE INDEX `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
    );

    INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);

    INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
