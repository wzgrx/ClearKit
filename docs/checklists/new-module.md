# 新模块检查清单

## 调研
- [ ] 同类上游全部克隆，每个都读了设置项、实现、近期 PR / issue
- [ ] `docs/upstream/<日期>-review.md` 对照文档（我们更强 / 没有 / 也有的问题）
- [ ] `docs/upstream/TASKS.md` 任务清单，标了空开关和不做的（绕过付费）
- [ ] 维护者选定了要做的

## 品牌
- [ ] 名字：宿主昵称 + 净界 / 英文 + Clear，维护者确认
- [ ] 强调色：种子色定了，浅色 / 深色两套对比度 ≥ 4.5
- [ ] 图标：意象维护者选定；`report.txt` 安全区通过；四种桌面效果看过；48 像素认得出
- [ ] 图标资源接进 `app/src/main/res`；母版、JSON、README 放 `docs/brand/`
- [ ] 主题图标线稿（单色）、快速设置磁贴
- [ ] 宿主进程里的小图（`brand_mark_192.png` 放进 hook 的 Java 资源）

## 代码骨架
- [ ] 从参考实现复制共享部分，改包名、应用名
- [ ] DexAdapt 引用固定提交（`gradle/dexadapt.commit`）
- [ ] `AGENTS.md`（模板 `templates/AGENTS.module.md`）、`CHANGELOG.md`

## 符号
- [ ] 每个渠道最新版 + 上一版安装包收集好
- [ ] 内置版本全部 `0 missing`，DexKit 一致性通过，版本矩阵更新

## 设置界面
- [ ] 设置盘点：每项 → 页面 → 有没有示意图
- [ ] 覆盖测试、绑定合法、一页一图 全过
- [ ] 过滤类有样例信息流，看不见的功能有证据卡

## 门禁和真机
- [ ] `scripts/verify.sh` 通过（单测、lint、签名 release 包）
- [ ] 真机冒烟通过；每个功能有真机结论；测试改过的设置都还原

## 发版
- [ ] CHANGELOG、版本号
- [ ] 维护者同意发 Release
- [ ] Release 有安装包；测试机装上新版
