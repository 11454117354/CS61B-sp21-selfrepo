# Gitlet Design Document

**Name**:Chen

## Classes and Data Structures


### Main
程序的入口 
- 读取命令行参数，根据第一个参数来判断调用哪个命令
- 调用Repository中对应的方法来执行业务逻辑
#### Fields


### Repository
管理仓库的状态
- 处理Gitlet命令
- 管理HEAD、分支表、暂存区
- 负责在对象存储中保存与读取Commit和Blob对象
#### Fields


### Commits
每次的commit存储的地方  
含有：  
- Message
- Timestamp
- Parent ID

### Blob
文件内容的快照  
含有：
- 文件名
- 文件内容

### StagingArea
暂存区：
- addedFiles
- removedFiles

## Algorithms
### init
### add
### commit
### checkout

## Persistence
通过将对象写入存储中实现：
````
.gitlet/
├── HEAD
├── objects/
│   ├── commits/
│   └── blobs/
├── refs/
│   └── heads/
├── staging/
│   ├── add/
│   └── remove/
````
Objects使用Java serialization写入存储。
