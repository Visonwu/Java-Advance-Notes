

[TOC]

# 1.设置用户名和邮箱：
git config --global user.name "John Doe"
git config --global user.email johndoe@example.com

# 2. 生成密匙：
ssh-keygen

--------------------------------
git身份验证失败清除密码缓存  解决方案：

git config --system --unset credential.helper

之后再进行git操作时，弹出用户名密码窗口，输入即可



1. mkdir doc  	创建文件夹
2. cd  doc  	进入文件
3. pwd  	获取当前目录全路径
4. git init 	可以把当前目录变成git可以管理的目录

# 3. 添加文件到git仓库，分两步

5. git add readme.txt 表示把文件添加到仓库
   git add . 添加所有文件到仓库
6. git commit -m "add readme" 把文件提交到仓库   -m 表示对于本次提交的的说明
--------

7. git status 	可以查看当前仓库的状态
8. git diff	可以查看我们文件修改的内容
# 4.版本设置

9. git log    	可以来查看版本

10. git reset --hard HEAD^ 可以回到上一个版本
    要是现在我们想回到add distributed版本，git中，用HEAD表示当前版本，上一个版本就是HEAD^，上上一个版本就是HEAD^^，
    当然往上100个版本写100个^比较容易数不过来，所以写成HEAD~100

11. git reset --hard 53b1c4d  	恢复到未来的记录，可以看到commit id 的情况下

12. git reflog 			要重返未来，用git reflog查看命令历史，以便确定要回到未来的哪个版本，在调用上面的方法

13. git checkout -- readme.txt   在修改了文件之后，且还没有add之前，可执行以下命令丢弃工作区的修改。
    命令git checkout -- readme.txt意思就是，把readme.txt文件在工作区的修改全部撤销，这里有两种情况：
    一种是readme.txt自修改后还没有被放到暂存区，现在，撤销修改就回到和版本库一模一样的状态；
    一种是readme.txt已经添加到暂存区后，又作了修改，现在，撤销修改就回到添加到暂存区后的状态。
    总之，就是让这个文件回到最近一次git commit或git add时的状态。

14. .git reset HEAD readme.txt
    如果你想要修改，已经git add到暂存区，但还没有commit的内容。
    我们可以用命令git reset HEAD file可以把暂存区的修改撤销掉（unstage），重新放回工作区：
    $ git reset HEAD readme.txt
    git reset命令既可以回退版本，也可以把暂存区的修改回退到工作区。当我们用HEAD时，表示最新的版本

    ​	

reset --hard & reset --soft & reset 不加参数
1. git reset --hard ：重置位置的同时，清空⼯工作⽬目录的所有改动；
2. git reset --soft ：重置位置的同时，保留留⼯工作⽬目录和暂存区的内容，并把重置  HEAD 的位置所导致的
  新的⽂文件差异放进暂存区。
3. git reset --mixed （默认）：重置位置的同时，保留留⼯工作⽬目录的内容，并清空暂存区	

# 5. 删除文件和恢复

15.rm test.txt	删除文件
16. git rm test.txt  
    git commit -m "remove test.txt"   删除并提交
17. .git checkout -- test.txt 恢复文件

# 6. 撤销操作

有时候我们提交完了才发现漏掉了几个文件没有添加，或者提交信息写错了。 此时，可以运行带有  --amend 选项的提交命令尝试重新提交
17.1 git commit --amend

------------
# 7. 连接到远程仓库

18. $ git remote add origin git@gitlab.com:Visonwu/learngit.git	关联远程库
19. git push -u origin 			master第一次推送master分支的所有内容；
20. git push origin master		此后每次提交用这个命令
# 8. 从远程克隆

21.$ git clone git@gitlab.alipay-inc.com:wb-zhanglailei/learngit.git

------
# 9. 分支管理

22. $ git checkout –b dev 创建并切换到dev分支	相当于 git branch dev  和 git checkout dev 两步
23. git branch 查看当前分支	git branch –a 查看当前仓库的所有分支；git branch –r查看远程仓库的所有分支
    git branch命令会列出所有分支，当前分支前面会标一个*号
    24.git checkout master 切换分支
       git merge dev   把dev分支工作成果合并到master上，这样就可以看到其他分支内容了
     分支上提交的文件，其他分支是看不到的
24. git branch -d dev  删除分支   合并完后可以删除dev分支
25.   git log --graph    命令可以看到分支合并图。 
# 10.  bug分支

27. git stash 	储藏当前工作现场
28. git stash list  修复bug后查看
    28.1 git stash pop   弹出当时储存的内容

# 11. 多人协作

29. git checkout -b dev origin/dev  创建远程dev到本地
    查看远程库信息，使用git remote -v；

    本地新建的分支如果不推送到远程，对其他人就是不可见的；

    从本地推送分支，使用git push origin branch-name，如果推送失败，先用git pull抓取远程的新提交；

    在本地创建和远程分支对应的分支，使用git checkout -b branch-name origin/branch-name，本地和远程分支的名称最好一致；

    建立本地分支和远程分支的关联，使用git branch --set-upstream branch-name origin/branch-name；

    从远程抓取分支，使用git pull，如果有冲突，要先处理冲突。

30. git pull = git fetch + git merge  相当于拉取在合并

# 12. 标签

命令git push origin 可以推送一个本地标签；

命令git push origin --tags可以推送全部未推送过的本地标签；

命令git tag -d 可以删除一个本地标签；

命令git push origin :refs/tags/可以删除一个远程标签。