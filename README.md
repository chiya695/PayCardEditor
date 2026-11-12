# PayCardEditor

我做了这个版本，让 PayCardEditor 能在 LSPosed 1.x 上运行。原项目主要面向 LSPosed 2.x；我把模块入口和加载声明改成了 1.x 可用的方式，卡片读取和自定义卡面功能照常保留。

我已经在自己的手机上测过：模块能激活、能读取卡片，也能制作并应用卡面。应用新卡面后，小米智能卡有时还会显示旧图；目前我会先停止小米智能卡，再重新打开它刷新。自动刷新还没有解决。

在 LSPosed 里启用模块，并把“小米智能卡”（`com.miui.tsmclient`）加入作用域。刚启用模块或更新后，重启一次小米智能卡。

## 插入 Logo

1.在“卡面制作”中长按已有卡面，选择“插入 Logo”。
2.点击“添加 Logo”，按分类选择内置标识，也可搜索名称或从手机导入图片。导入的图片保存在“我的 Logo”，下次可以继续使用。
3.点选标识后拖动调整位置，双指或滑块调整大小，并按需修改不透明度。可添加多个标识。
4.点击“另存为新卡面”，输入名称后保存。原卡面和正在使用它的卡片不会改变；请在列表中选择新卡面并应用。

内置 101 个标识，分为金融、交通卡、街机卡、其他标识和待确认。透明标识可直接叠加；有底色的图片保留底色，不自动抠图。手机导入支持 PNG、JPEG、WebP，单张不超过 32 MB。保存结果是合成图片，已保存的标识不能作为独立图层重新调整；需要重做时可从保留的原卡面开始。

## 原理

我通过 Hook 卡片数据和图片加载过程来替换卡面，不直接改钱包里的原始卡片数据。

## 感谢

这个项目使用了以下开源项目的部分或全部内容，感谢各位开发者：

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
