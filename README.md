# ShortCutFileUtil
Windowsショートカットの作成とリンク先の取得

### 作成
ShortCutFileUtil.createShortCut(fileName, linkPath, desc, args, workDir)

### リンク先の取得
val linkPath = ShortCutFileUtil.getLinkPath(fileName)
