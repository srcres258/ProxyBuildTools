
ProxyBuildTools
================

内置代理路由功能的 Spigot BuildTools

绪论
----
Spigot 服务端核心的官方构建工具 BuildTools 由于未内置代理功能，导致在中国大陆使用时下载网络资源时速度十分缓慢。笔者曾尝试过普适的 Java 应用程 
序代理配置方式，如设置环境变量 `HTTP_PROXY` 、 JVM 参数 `-Dhttp.proxyHost=xxx.xxx.xxx.xxx` 和 `-Dhttp.proxyPort=xxx.xxx.xxx.xxx`
等，但 对于 BuildTools 均无效。势必需要修改 BuildTools 的代码逻辑以达成使用网络代理的目的了。

于是 ProxyBuildTools 应运而生。该 BuildTools 分支版本为程序添加了三个参数以指定 BuildTools 在构建服务端核心时所使用的代理，使得
BuildTools 在下载相关网络资源时将网络路由至相应的网络代理，以显著提高构建速度。

鉴于作者精力有限，目前 ProxyBuildTools 仅支持 **HTTP 代理**，未来将计划支持 SOCKS 代理。

使用方法
--------
可通过 BuildTools.jar 的 `--help` 选项以查看程序相关参数的简介。大多数参数选项与 Spigot 官方版本相同，因此仅介绍该分支版本新增的参数选项。

- `--proxy`：声明程序将使用代理。必须添加该选项，后面的两个选项才会生效，亦即 `--proxy` `--proxy-addr` `--proxy-port` 三个选项必须同时指定。
- `--proxy-addr`：指定代理程序的主机名称。可为 IP 地址、域名或者 localhost 等任一合法主机名称。
- `--proxy-port`：指定代理程序的端口号。

开源协议
--------
本项目使用与 Spigot 相同的 LGPL v3 协议授权，详见项目根目录下的 [LICENSE](./LICENSE) 和 [lgpl-3.0.txt](./LICENSE) 文件。
