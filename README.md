# tinydownloader

> demo

```
 speed: 3.44 MB/s  success: 44  fail: 0  skip: 0  pending: 1443
+--+--------------------------------------+----------------------+----------+------------+
|# |               filename               |       percent        |   size   |   speed    |
+--+--------------------------------------+----------------------+----------+------------+
|1 |1523879069738.jpg                     | ==================>- |   1.93 MB| 411.13 KB/s|
|2 |1523878733043.jpg                     | =>------------------ | 925.24 KB|  85.05 KB/s|
|3 |1523878722260.jpg                     | ===>---------------- | 874.72 KB| 134.48 KB/s|
|4 |1523878702859.jpg                     | -------------------- | 830.51 KB|    0.00 B/s|
|5 |1523878740068.jpg                     | =======>------------ | 782.94 KB| 479.05 KB/s|
|6 |1523878753542.jpg                     | =====>-------------- | 595.29 KB|  67.21 KB/s|
|7 |1523878736682.jpg                     | ==========>--------- | 537.66 KB| 563.45 KB/s|
|8 |1523878718740.jpg                     | -------------------- | 942.16 KB|  27.37 KB/s|
|9 |1523878821407.jpg                     | ========>----------- |   1.32 MB| 268.10 KB/s|
|10|1523878889318.jpg                     | =====>-------------- |   1.23 MB|  32.33 KB/s|
+--+--------------------------------------+----------------------+----------+------------+
```
[![temp.gif](https://i.loli.net/2018/11/05/5bdffa5a954a6.gif)](https://i.loli.net/2018/11/05/5bdffa5a954a6.gif)


> how to use

windows & jdk >= 1.8

1. 运行 `compile.bat` 编译程序

2. 将需要下载的列表文件 (txt) 拖放在 `tinydownload.bat` 上来进行加载
   或
   直接运行 `tinydownload.bat` 将会加载 `./resources/urls.txt`

3. 输入文件保存的目录 ` ( 如果为空，那么会将下载的文件保存在 txt 所在目录 ) `