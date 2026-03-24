# -*- coding: utf-8 -*-
"""
服务端配置
"""

# Android 设备 HTTP 服务地址
# 这里使用 adb reverse 后的本机地址，适合当前真机联调
DEVICE_API_BASE = "http://127.0.0.1:9527"

# ADB 路径（用于实时画面截图）
ADB_PATH = "C:\\platform-tools\\adb.exe"

# 多设备配置
DEVICES = {
    "device_1": {
        "name": "设备1-企业微信",
        "api_base": "http://127.0.0.1:9527",
        "target_app": "wework",
        "adb_serial": "d1c488854f9e",
    },
}

# 任务轮询间隔（秒）
TASK_POLL_INTERVAL = 2

# 任务超时时间（秒）
TASK_TIMEOUT = 60

# 服务端 API 端口
SERVER_PORT = 8080

# 日志配置
LOG_LEVEL = "INFO"
LOG_FILE = "rpa_server.log"
