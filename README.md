# PayCardEditor 

这是一个 Xposed 模块，可以帮助你替换自己的 Mi Pay 卡面为自己想要的图片。
## 关于这个分支

这个仓库是从原项目分出来的，主要是为了让使用 LSPosed 1.x（本次按 LSPosed 1.9.2、API 100 调整）的人也能测试使用。原项目使用的 libxposed API 版本和本机环境不匹配，所以我把模块入口、Hook 写法和模块声明改成 API 100 能识别的形式，并调整了相关依赖。卡面替换功能本身没有打算另起一套，重点是兼容旧版 LSPosed。

单独维护这个分支，是为了不改动原作者的仓库，也不把面向 LSPosed 2.x/API 101 的更新和面向 LSPosed 1.x/API 100 的兼容修改混在一起。以后可以分别跟进上游更新和这边的兼容问题；使用时按自己的 LSPosed 版本选择对应版本即可。

本分支改动包括：

- 将 libxposed API / service 依赖调整到 API 100。
- 将模块入口改为 API 100 的加载方式，并把原有 Hook 调用接到 API 100 的 Hook API。
- 调整 Hook 日志、模块声明和相关兼容代码。
- 保留原来的 Mi Pay 卡面替换逻辑；本次主要改的是框架兼容，不是新增卡面功能。

已在实际设备上验证 LSPosed 1.x 下模块激活、读取卡片和应用自定义卡面。当前已知限制：应用卡面后，小米智能卡可能仍显示旧内容，需要停止并重新打开小米智能卡以刷新。

## 原理

本模块采用 Hook 架构，从数据源头和图片加载两个层面实现卡面替换，相比传统的缓存文件直接替换方案更加稳定可靠。

## 感谢

> PayCardEditor 使用了以下开源项目的部分或全部内容，感谢这些项目的开发者们做出的贡献。

| Project Name      | Author/Organization                      | Link                                        |
|-------------------|------------------------------------------|---------------------------------------------|
| Android           | Android Open Source Project, Google Inc. | https://source.android.google.cn/license    |
| AndroidX          | Android Open Source Project, Google Inc. | https://github.com/androidx/androidx        |
| libxposed-service | libxposed                                | https://github.com/libxposed/service        |
| libxposed-api     | libxposed                                | https://github.com/libxposed/api            |
| DexKit            | LuckyPray                                | https://github.com/LuckyPray/DexKit         |
| MiPayCard         | gddhy                                    | https://github.com/gddhy/MiPayCard          |
| HyperCeiler       | ReChronoRain                             | https://github.com/ReChronoRain/HyperCeiler |
| QAuxiliary        | cinit                                    | https://github.com/cinit/QAuxiliary         |
| Ucrop             | yalantis                                 | https://github.com/yalantis/ucrop           |
| Glide             | bumptech                                 | https://github.com/bumptech/glide           |
