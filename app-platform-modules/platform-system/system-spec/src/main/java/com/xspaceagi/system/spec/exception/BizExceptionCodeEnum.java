package com.xspaceagi.system.spec.exception;

import lombok.Getter;

/**
 * 异常枚举
 */
@Getter
public enum BizExceptionCodeEnum implements IBizExceptionCodeEnum {


    /**
     * 系统异常
     */
    SYSTEM_ERROR_1000("1000", "系统异常，请稍后再试", "系统未预料统一捕获的异常，例如空指针、数组越界等"),
    SYSTEM_ERROR_1010("1010", "系统异常，请稍后再试", "参数异常，必填参数未传递"),
    SYSTEM_ERROR_1020("1020", "系统异常，请稍后再试", "依赖服务（内部系统RPC）调用异常，超时、服务不可用等"),
    SYSTEM_ERROR_1030("1030", "系统异常，请稍后再试", "三方服务（外部系统HTTP）调用异常，超时、对方系统异常等"),
    SYSTEM_ERROR_1040("1040", "系统异常，请稍后再试", "数据层（MYSQL、ES等）异常，主键冲突、超时等"),
    SYSTEM_ERROR_1050("1050", "系统异常，请稍后再试", "缓存异常"),


    //参数缺失提示出去
    VALIDATE_ERROR_2006("2006", "%s", "参数校验失败"),
    BIZ_ERROR_2007("2007", "%s不能为空", "参数缺失提示出去"),

    //系统管理
    SYSTEM_MANAGE_NOT_EXIST_ERROR_3001("3001", "用户不存在", "用户不存在"),
    SYSTEM_MANAGE_ERROR_3002("3002", "用户启用状态标记异常", "用户启用状态标记异常"),
    SYSTEM_MANAGE_ERROR_3003("3003", "用户或密码错误", "用户或密码错误"),
    SYSTEM_MANAGE_ERROR_3004("3004", "用户或手机号已存在", "用户或手机号已存在"),
    SYSTEM_MANAGE_ERROR_3005("3005", "机构不存在", "机构不存在"),
    SYSTEM_MANAGE_ERROR_3006("3006", "备份文件不存在,文件=%s", "备份文件不存在"),
    SYSTEM_MANAGE_ERROR_3007("3007", "备份记录不存在", "备份记录不存在"),
    SYSTEM_MANAGE_ERROR_3008("3008", "备份文件已存在,为避免覆盖文件,请重试备份操作", "备份文件已存在,为避免覆盖文件,请重试备份操作"),
    SYSTEM_MANAGE_ERROR_3009("3009", "下载备份文件时发生IO异常,%s", ""),
    SYSTEM_MANAGE_ERROR_3010("3010", "密码错误", ""),
    SYSTEM_MANAGE_ERROR_3011("3011", "新密码不能和旧密码相同", ""),
    SYSTEM_MANAGE_ERROR_3012("3012", "菜单不存在,KEY=%s", ""),

    //加密解密异常
    CIPHER_ERROR_4001("4001", "解密失败", "解密失败"),
    CIPHER_ERROR_4002("4002", "加密失败", "加密失败"),

    //知识库
    KNOWLEDGE_ERROR_5001("5001", "数据不存在", "知识库不存在"),
    KNOWLEDGE_ERROR_5003("5002", "模板指令类型不存在", "模板指令类型不存在"),
    KNOWLEDGE_ERROR_5004("5004", "参数ID不能为空", "请求参数ID不能为空"),
    KNOWLEDGE_ERROR_5005("5005", "未识别的分析维度类型[%s]", "未识别的分析维度类型"),
    KNOWLEDGE_ERROR_5006("5006", "数据映射识别,异常=%s", "excel数据映射记录"),
    KNOWLEDGE_ERROR_5007("5007", "Excel相关数据不能为空", "Excel相关数据不能为空"),
    KNOWLEDGE_ERROR_5008("5008", "Excel抬头字段信息不能为空", "Excel抬头字段信息不能为空"),
    KNOWLEDGE_ERROR_5009("5009", "模板ID不能为空", ""),
    KNOWLEDGE_ERROR_5010("5010", "评估ID不能为空", ""),
    KNOWLEDGE_ERROR_5011("5011", "向量数据库[{}]不存在", ""),
    KNOWLEDGE_ERROR_5012("5012", "文件ID不存在", ""),
    KNOWLEDGE_ERROR_5013("5013", "请求参数不能为空", ""),
    KNOWLEDGE_ERROR_5014("5014", "解析文档失败", ""),
    KNOWLEDGE_ERROR_5015("5015", "解析文档失败,错误:不支持的文件类型", ""),
    KNOWLEDGE_ERROR_5016("5016", "大模型返回的答案为空,分段id=%s", ""),
    KNOWLEDGE_ERROR_5017("5017", "大模型返回的问题为空,分段id=%s", ""),
    KNOWLEDGE_ERROR_5018("5018", "知识库ID为空", ""),
    KNOWLEDGE_ERROR_5019("5019", "导入文件失败,%s", ""),
    KNOWLEDGE_ERROR_5020("5020", "生成模板失败：,%s", ""),
    KNOWLEDGE_ERROR_5021("5021", "知识库不存在,知识库ID=%s", ""),  
    KNOWLEDGE_ERROR_5022("5022", "向量化失败,%s", ""),
    KNOWLEDGE_ERROR_5023("5023", "删除知识库全文检索数据失败", ""),
    KNOWLEDGE_ERROR_5024("5024", "删除文档全文检索数据失败", ""),
    KNOWLEDGE_ERROR_5025("5025", "删除原始分段全文检索数据失败", ""),
    KNOWLEDGE_ERROR_5026("5026", "解析JSON文档失败,JSON格式不支持,期望JSON数组或JSON对象", ""),

    //组件模块
    COMPOSE_ERROR_6001("6001", "数据不存在", ""),
    COMPOSE_ERROR_6002("6002", "无法获取建表DDL语句", ""),
    COMPOSE_ERROR_6003("6003", "解析Doris数据库名称失败: %s", ""),
    COMPOSE_ERROR_6004("6004", "只允许执行 SELECT/INSERT/UPDATE/DELETE 查询语句", ""),
    COMPOSE_ERROR_6005("6005", "执行的SQL语句不能为空", ""),
    COMPOSE_ERROR_6006("6006", "执行SQL失败,%s", ""),
    COMPOSE_ERROR_6007("6007", "id不能为空", ""),
    COMPOSE_ERROR_6008("6008", "创建表失败", ""),
    COMPOSE_ERROR_6009("6009", "行ID不能为空", ""),
    COMPOSE_ERROR_6010("6010", "无法构建更新的 SET 子句", ""),
    COMPOSE_ERROR_6011("6011", "更新的数据不能为空", ""),
    COMPOSE_ERROR_6012("6012", "表Id不能为空", ""),
    COMPOSE_ERROR_6013("6013", "表不存在", ""),
    COMPOSE_ERROR_6015("6015", "SQL解析失败,%s", ""),
    COMPOSE_ERROR_6016("6016", "只允许DDL语句,sql=%s", ""),
    COMPOSE_ERROR_6017("6017", "查询全部数据失败", ""),
    COMPOSE_ERROR_6018("6018", "表中已有数据，不允许删除字段", ""),
    COMPOSE_ERROR_6019("6019", "字段[%s]已有数据，不允许修改唯一性约束", ""),
    COMPOSE_ERROR_6020("6020", "字段[%s]已有数据，不允许修改非空约束", ""),
    COMPOSE_ERROR_6021("6021", "未配置数据库类型,配置:spring.datasource.sql-generator.type", ""),
    COMPOSE_ERROR_6022("6022", "不支持的数据库类型: %s, 仅支持 mysql 或 doris", ""),
    COMPOSE_ERROR_6023("6023", "未配置Doris数据源URL,配置:spring.datasource.dynamic.datasource.doris.url", ""),
    COMPOSE_ERROR_6024("6024", "插入的数据不能为空", ""),
    COMPOSE_ERROR_6025("6025", "表定义不存在", ""),
    COMPOSE_ERROR_6026("6026", "创建索引失败", ""),
    COMPOSE_ERROR_6027("6027", "字段名不能为空", ""),
    COMPOSE_ERROR_6028("6028", "字段名长度不能超过64字符", ""),
    COMPOSE_ERROR_6029("6029", "字段名必须以英文字母开头", ""),
    COMPOSE_ERROR_6030("6030", "字段名只能包含字母、数字和下划线", ""),
    COMPOSE_ERROR_6031("6031", "检查表是否存在时发生错误，数据库,%s", ""),
    COMPOSE_ERROR_6032("6032", "清空表数据时发生错误，数据库,%s", ""),
    COMPOSE_ERROR_6033("6033", "获取表定义信息时发生错误，数据库,%s", ""),
    COMPOSE_ERROR_6034("6034", "执行原始管理/DML/DDL的SQL异常,%s", ""),
    COMPOSE_ERROR_6035("6035", "不支持的SQL类型,SqlType=%s", ""),
    COMPOSE_ERROR_6036("6036", "创建条件表达式失败,%s", ""),
    COMPOSE_ERROR_6037("6037", "字段[%s]的默认值必须为数值", ""),
    COMPOSE_ERROR_6038("6038", "字段[%s]的默认值必须为布尔值", ""),
    COMPOSE_ERROR_6039("6039", "字段[%s]的默认值长度不能超过255字符", ""),
    COMPOSE_ERROR_6040("6040", "MEDIUMTEXT类型字段[%s]不允许设置默认值", "MEDIUMTEXT类型字段不允许设置默认值"),
    COMPOSE_ERROR_6041("6041", "Excel数据行数超过限制,最大支持[%s]行", ""),
    COMPOSE_ERROR_6042("6042", "表总行数超过限制,最大支持[%s]行", ""),
    COMPOSE_ERROR_6043("6043", "表定义缺少字段定义", ""),
    COMPOSE_ERROR_6044("6044", "字段[%s]已存在", ""),
    COMPOSE_ERROR_6045("6045", "字段[%s]的默认值超出范围,范围区间:[%s,%s]", ""),
    COMPOSE_ERROR_6046("6046", "创建人id不能为空", ""),
    COMPOSE_ERROR_6047("6047", "%s", ""),



    //日志平台
    LOG_PLATFORM_ERROR_7001("7001", "搜索失败,%s", "搜索失败"),
    LOG_PLATFORM_ERROR_7002("7002", "新增失败,%s", "新增失败"),
    LOG_PLATFORM_ERROR_7003("7003", "批量新增失败,%s", "批量新增失败"),

    //生态市场
    ECO_MARKET_ERROR_8001("8001", "配置不存在", "配置不存在"),
    ECO_MARKET_ERROR_8002("8002", "获取配置异常,%s", "获取配置详情异常"),
    ECO_MARKET_ERROR_8003("8003", "发布配置异常,%s", "发布配置异常"),
    ECO_MARKET_ERROR_8004("8004", "下线配置异常,%s", "下线配置异常"),
    ECO_MARKET_ERROR_8005("8005", "查询配置状态异常,%s", "查询配置状态异常"),
    ECO_MARKET_ERROR_8006("8006", "查询发布配置详情异常,%s", "查询发布配置详情异常"),
    ECO_MARKET_ERROR_8007("8007", "配置UID不能为空", "配置UID不能为空"),
    ECO_MARKET_ERROR_8008("8008", "分享状态不能为空", "分享状态不能为空"),
    ECO_MARKET_ERROR_8009("8009", "配置模型不能为空", "配置模型不能为空"),
    ECO_MARKET_ERROR_8010("8010", "远程生态市场已上架的配置不能删除", ""),
    ECO_MARKET_ERROR_8011("8011", "获取客户端密钥失败，请稍后重试", "获取客户端密钥失败"),
    ECO_MARKET_ERROR_8012("8012", "只能更新草稿或被驳回状态的配置", "只能更新草稿或被驳回状态的配置"),
    ECO_MARKET_ERROR_8013("8013", "无权更新此配置", "无权更新此配置"),
    ECO_MARKET_ERROR_8014("8014", "未找到对应的配置信息", "未找到对应的配置信息"),
    ECO_MARKET_ERROR_8015("8015", "未找到对应配置信息", "未找到对应配置信息"),
    ECO_MARKET_ERROR_8016("8016", "配置模型不能为空且UID必须指定", "配置模型不能为空且UID必须指定"),
    ECO_MARKET_ERROR_8017("8017", "未找到对应的发布配置信息", "未找到对应的发布配置信息"),
    ECO_MARKET_ERROR_8018("8018", "目标ID和目标类型不能为空", "目标ID和目标类型不能为空"),
    ECO_MARKET_ERROR_8019("8019", "不支持的数据类型", ""),
    ECO_MARKET_ERROR_8020("8020", "发版失败,%s", ""),
    ECO_MARKET_ERROR_8021("8021", "保存配置异常,%s", ""),
    ECO_MARKET_ERROR_8022("8022", "下线配置异常,%s", ""),
    ECO_MARKET_ERROR_8023("8023", "启用配置失败", ""),
    ECO_MARKET_ERROR_8024("8024", "配置JSON不能为空", "配置JSON不能为空"),
    ECO_MARKET_ERROR_8025("8025", "已有重复的配置,不允许新建我的分享", ""),
    ECO_MARKET_ERROR_8026("8026", "生态市场中心服务升级中，请稍后再试", ""),
    ECO_MARKET_ERROR_8027("8027", "获取配置结果失败,Body为空", ""),
    ECO_MARKET_ERROR_8028("8028", "租户ID不能为空", "租户ID不能为空"),
    ECO_MARKET_ERROR_8029("8029", "MCP配置不能为空", "MCP配置不能为空"),
    ECO_MARKET_ERROR_8030("8030", "部署MCP失败", "部署MCP失败"),
    ECO_MARKET_ERROR_8031("8031", "停止MCP失败", "停止MCP失败"),
    ECO_MARKET_ERROR_8032("8032", "MCP配置解析失败", "MCP配置解析失败"),
    ECO_MARKET_ERROR_8033("8033", "禁用配置失败", ""),
    ECO_MARKET_ERROR_8034("8034", "只有已发布/审核中状态的配置可以下线", ""),
    ECO_MARKET_ERROR_8035("8035", "下线服务器配置失败，请稍后重试", ""),
    ECO_MARKET_ERROR_8036("8036", "未发布,无需下线", ""),
    ECO_MARKET_ERROR_8037("8037", "只有审核中状态的配置可以撤销发布", ""),
    ECO_MARKET_ERROR_8038("8038", "禁止分享生态市场获取的配置", ""),
    ECO_MARKET_ERROR_8039("8039", "智能体未绑定页面,不能分享为应用页面", ""),
    ECO_MARKET_ERROR_8040("8040", "页面导出失败", ""),
    ECO_MARKET_ERROR_8041("8041", "页面压缩包URL不能为空", ""),
    ECO_MARKET_ERROR_8042("8042", "下载页面压缩包失败", ""),
    ECO_MARKET_ERROR_8043("8043", "创建页面项目失败", ""),
    ECO_MARKET_ERROR_8044("8044", "上传页面压缩包失败", ""),
    ECO_MARKET_ERROR_8045("8045", "发布页面失败", ""),
    ECO_MARKET_ERROR_8046("8046", "应用页面启用失败", ""),
    ECO_MARKET_ERROR_8047("8047", "只能删除自己分享的配置", ""),


    //AI 开发自定义页面
    CUSTOM_PAGE_ERROR_6001("6001", "数据不存在", "评估页面不存在"),

    CUSTOM_PAGE_ERROR_9010("9010", "Agent正在执行任务，请等待当前任务完成后再发送新请求", "Agent正在执行任务，请等待当前任务完成后再发送新请求;前端全局处理了这个错误码"),


    SUCCESS_CODE("0000", "成功", "成功");


    /**
     * 错误码
     */
    private final String code;


    /**
     * 错误信息
     */
    private final String message;
    /**
     * 分类备注,方便看异常定义,不参与错误提示
     */
    private final String remark;


    BizExceptionCodeEnum(String code, String message, String remark) {
        this.code = code;
        this.message = message;
        this.remark = remark;
    }


}
