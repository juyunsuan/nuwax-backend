package com.xspaceagi.agent.core.spec.enums;

public class CodeConstant {

    public static final String DEFAULT_CODE_JS = """
            //引入js插件,支持多种形式,可以是http(s)、npm包、jsr网站ESM模块依赖、或node内置的工具，也可以去网站: https://deno.land/x  搜索自己所需插件
            //网络请求,可以直接使用fetch，具体自行查阅fetch文档
            //入参: 入参统一封装在args中，例如：args.a、args.b
            //出参: 必须是JSON对象，例如：{message:"hello"}，出参是message
            //依赖示例
            //import * as o from 'https://deno.land/x/cowsay/mod.ts'
            //import axios from 'npm:axios';
            //import { Buffer } from "node:buffer";
            //import { delay } from "jsr:@std/async";

            //如果有依赖，首次执行比较慢可能会超时，试运行如果超时，可过几分钟再试
            // 入口函数不可修改，否则无法执行，args 为配置的入参
            export default async function main(args) {
                // 构建输出对象，出参中的key需与配置的出参保持一致
                return {
                    'key': 'value',
                };
            }
            """;

    public static final String DEFAULT_CODE_PYTHON = """
            # 使用 import 来引入依赖,或者 importlib 动态导入依赖
            # 下面是一个引入示例，创建一个简单的 DataFrame
            # import pandas as pd
            # 
            #data = {
            #    'Name': ['Alice', 'Bob', 'Charlie'],
            #    'Age': [25, 30, 35]
            #}
            #df = pd.DataFrame(data)
            
            # 如果有依赖，首次执行比较慢可能会超时，试运行如果超时，可过几分钟再试
            # 入口函数不可修改，否则无法执行，args 为配置的入参
            def main(args: dict) -> dict:
            
                params = args.get("params")
                # 构建输出对象，出参中的key需与配置的出参保持一致
                ret = {
                    "key": "value"
                }
                return ret
            """;

}
