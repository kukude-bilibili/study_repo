# CompletableFuture 详细解读（结合 Agent 权限交互场景）

## 一、基础概念

`CompletableFuture` 是 Java 8 引入的**异步回调工具类**，核心作用：

1. 封装一段异步等待结果的流程
2. 可以主动手动设置最终结果，唤醒阻塞等待的线程
3. 泛型 `PermissionResponse` = 最终要拿到的返回值类型（用户权限选择：同意 ALLOW / 拒绝 DENY）

---

## 二、套用到 PermissionRequestEvent 场景

### 整体流程拆解

#### 1. Agent 端发起权限申请

Agent 想要调用工具，但需要用户授权，于是构造 `PermissionRequestEvent` 事件：

```java
new PermissionRequestEvent(工具名, 权限说明, future对象);
```

把 future 放进事件里，发送给前端 UI 界面。
此时 Agent 线程会调用 `future.get()` / `join()` **阻塞暂停**，卡在原地等待用户做选择。

#### 2. UI 界面接收事件，弹出权限确认弹窗

前端拿到事件：展示工具用途、权限描述，给用户两个按钮：允许 / 拒绝。

#### 3. 用户做出选择后，UI 回填结果到 future

用户点击按钮后，UI 拿到选择结果（PermissionResponse），调用：

```java
future.complete(用户选择的权限结果);
```

`complete()` 会**给 future 塞入最终返回值**，立刻唤醒刚才阻塞休眠的 Agent 线程。

#### 4. Agent 线程恢复执行

阻塞结束，`future.get()` 返回 `PermissionResponse`，Agent 判断用户是同意还是拒绝：

- 同意：正常调用工具
- 拒绝：终止本次工具调用

---

## 三、为什么必须用 CompletableFuture，不能普通返回值？

### 1. 跨线程异步等待

Agent 后端线程和 UI 渲染线程是两个独立线程。
Agent 不能一直轮询死循环去查用户有没有点按钮，会浪费 CPU；
CompletableFuture 提供了优雅的阻塞 + 唤醒机制。

### 2. 手动可控完成

普通 Future 只能由异步任务跑完自动返回结果；
而这里**结果由用户手动交互产生**，需要外部主动调用 `complete()` 赋值，这正是 CompletableFuture 的独有能力。

### 3. 泛型约束

`PermissionResponse` 专门承载权限答复数据：

```java
// 示例结构
enum PermissionResponse {
    ALLOW, DENY
}
```

保证拿到的结果类型安全。

---

## 四、对应 AskUserRequestEvent 的共性逻辑

`CompletableFuture<Map<String, String>>` 逻辑完全一致：

- Agent 等待用户填写表单问题
- UI 收集用户填写的问答键值对
- 调用 `future.complete(问答Map)` 唤醒 Agent

---

## 五、极简通俗总结

> future 就是一个**结果等待信箱**：

1. Agent 把信箱交出去，然后原地等着收信
2. 用户在前端做完选择，把答复塞进信箱
3. 信箱收到内容，Agent 立刻醒来取出答复继续运行