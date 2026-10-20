# 发版清单

- [ ] 所有改动已提交，`git status` 干净
- [ ] `CHANGELOG.md` 新的一节：用户能看懂的改动说明；没做的、没测到的也写
- [ ] `gradle.properties` 版本号升了（versionCode 只增不减）
- [ ] 门禁通过（`scripts/verify.sh` 或提交脚本）
- [ ] 真机冒烟通过，新功能有真机结论
- [ ] 测试改过的设置、留下的文件和计数都还原 / 删掉
- [ ] **维护者同意发 Release**
- [ ] `gh release create v<版本> --repo <仓库> --target <提交> -F <说明文件> <APK>`
- [ ] 测试机装上新版本，冒烟一次
