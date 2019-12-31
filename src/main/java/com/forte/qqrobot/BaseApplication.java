package com.forte.qqrobot;

import com.alibaba.fastjson.util.TypeUtils;
import com.forte.plusutils.consoleplus.console.Colors;
import com.forte.qqrobot.anno.Config;
import com.forte.qqrobot.anno.CoreVersion;
import com.forte.qqrobot.anno.depend.AllBeans;
import com.forte.qqrobot.depend.DependCenter;
import com.forte.qqrobot.depend.DependGetter;
import com.forte.qqrobot.exception.RobotRuntimeException;
import com.forte.qqrobot.listener.intercept.MsgIntercept;
import com.forte.qqrobot.listener.invoker.ListenerFilter;
import com.forte.qqrobot.listener.invoker.ListenerManager;
import com.forte.qqrobot.listener.invoker.ListenerMethodScanner;
import com.forte.qqrobot.listener.invoker.plug.Plug;
import com.forte.qqrobot.log.QQLog;
import com.forte.qqrobot.log.QQLogBack;
import com.forte.qqrobot.safe.PoliceStation;
import com.forte.qqrobot.scanner.FileScanner;
import com.forte.qqrobot.scanner.Register;
import com.forte.qqrobot.scanner.ScannerManager;
import com.forte.qqrobot.sender.MsgSender;
import com.forte.qqrobot.sender.senderlist.SenderGetList;
import com.forte.qqrobot.sender.senderlist.SenderSendList;
import com.forte.qqrobot.sender.senderlist.SenderSetList;
import com.forte.qqrobot.timetask.TimeTaskManager;
import com.forte.qqrobot.utils.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 启动类总抽象类，在此实现部分通用功能
 * 实现closeable接口
 * @param <CONFIG> 对应的插件配置类类型
 * @param <SP_API> 由组件实现方提供的特殊API对象。
 *                此类型没有任何限制，一般情况下我希望此类型是提供于我提供的三大API接口中不存在的API。
 *                例如：获取插件信息等等。
 *                有时候，这个类型可能就是你实现了三大API接口的那个对象
 * @author ForteScarlet <[163邮箱地址]ForteScarlet@163.com>
 * @date Created in 2019/3/29 10:18
 * @since JDK1.8
 **/
@CoreVersion(
        version = "",
        author = "ForteScarlet",
        email = "ForteScarlet@163.com"
)
public abstract class BaseApplication<CONFIG extends BaseConfiguration, SP_API> implements Closeable {

    //java版本检测
    static{
        //初始化警察局
//        PoliceStation.getInstance();
        try{
            //获取java版本
            String javaVersion = System.getProperties().getProperty("java.version");
            final int needVersion = 8;
            boolean largerThan8 = Integer.parseInt(javaVersion.split("_")[0].split("\\.")[1]) >= needVersion;
            if(!largerThan8){
                Colors colors = Colors.builder()
                        .addNoColor("检测到您的版本号为 ")
                        .add(javaVersion, Colors.FONT.RED)
                        .addNoColor(", 小于")
                        .add(" 1.8 ", Colors.FONT.BLUE)
                        .add("版本。")
                        .add("本框架基于1.8实现，推荐您切换为1.8或以上版本。")
                        .build();
                QQLog.warning(colors);
                QQLog.warning("假如您的版本在1.8或以上，请忽略这两条信息。");
            }
        }catch (Exception e){
            QQLog.warning("java版本号检测失败, 请尽可能确保您的java版本在1.8以上");
        }
    }



    /** 没有监听函数的送信器 */
    private MsgSender NO_METHOD_SENDER;

    /** 注册器，赋值在扫描方法结束后 */
    private Register register;

    /** 依赖获取器，赋值在配置后 */
    private DependGetter dependGetter;

    /**
     * 线程工厂初始化
     */
    protected void threadPoolInit(CONFIG config){
        //创建并保存线程池
        ResourceDispatchCenter.saveThreadPool(config.getPoolConfig());
    }

    /**
     * 公共资源初始化
     */
    private void baseResourceInit(){
        //将CQCodeUtil放入资源调度中心
        ResourceDispatchCenter.saveCQCodeUtil(CQCodeUtil.build());
        //将ListenerMethodScanner放入资源调度中心
        ResourceDispatchCenter.saveListenerMethodScanner(new ListenerMethodScanner());
        //将ListenerFilter放入资源调度中心
        ResourceDispatchCenter.saveListenerFilter(new ListenerFilter());
    }

    /**
     * 定时任务初始化
     */
    private void timeTaskInit(){
        //将定时任务类添加到资源调度中心
        ResourceDispatchCenter.saveTimeTaskManager(new TimeTaskManager());
//        将定时任务工厂添加到资源调度中心
//        ResourceDispatchCenter.saveStdSchedulerFactory(new StdSchedulerFactory());
    }

    // 设置日志输出等级
    private void logInit(CONFIG config){
        QQLog.setGlobalLevel(config.getLogLevel());
    }

    /**
     * 对fastJson进行配置
     */
    private void fastJsonInit(){
        //设置FastJson配置，使FastJson不会将开头大写的字段默认变为小写
        TypeUtils.compatibleWithJavaBean = true;
    }

    /**
     * 开发者实现的资源初始化
     * 此方法将会在所有的初始化方法最后执行
     * 增加一个参数
     * 此资源配置将会在配置之后执行
     */
    protected abstract void resourceInit(CONFIG config);
    /**
     * 开发者实现的资源初始化
     * 此方法将会在所有的初始化方法最后执行
     * 这个没有参数的将会在配置之前执行，建议从此处配置配置类实例化
     */
    protected abstract void resourceInit();

    //**************** 获取三种送信器 ****************//

    /**
     * 获取消息发送接口, 将会在连接成功后使用
     */
    protected abstract SenderSendList getSender();

    /**
     * 获取事件设置接口, 将会在连接成功后使用
     */
    protected abstract SenderSetList getSetter();

    /**
     * 获取资源获取接口, 将会在连接成功后使用
     */
    protected abstract SenderGetList getGetter();

    /**
     * 获取特殊API对象
     */
    public abstract SP_API getSpecialApi();

    /**
     * 开发者实现的启动方法
     * v1.1.2-BETA后返回值修改为String，意义为启动结束后打印“启动成功”的时候使用的名字
     * 例如，返回值为“server”，则会输出“server”启动成功
     *
     * v1.4.1之后增加一个参数：dependCenter
     *
     * @param dependCenter 依赖管理器，可以支持组件额外注入部分依赖。
     * @param manager      监听管理器，用于分配获取到的消息
     */
    protected abstract String start(DependCenter dependCenter, ListenerManager manager);

    /**
     * 开发者实现的获取Config对象的方法,对象请保证每次获取的时候都是唯一的
     * 此方法将会最先被执行
     */
    protected abstract CONFIG getConfiguration();



    //**************** 以下是一些不强制但是可以通过重写来拓展功能的方法 ****************//

    /**
     * 依赖扫描之前
     * @param config 配置文件
     * @param app    启动器接口实现类
     * @param register 注册器
     */
    protected void beforeDepend(CONFIG config, Application<CONFIG> app, Register register){
    }

    /**
     * 依赖扫描之后
     * 同时也是监听函数扫描之前
     * @return 所有的执行任务
     */
    protected void afterDepend(CONFIG config, Application<CONFIG> app, Register register, DependCenter dependCenter){
    }


    /**
     * 监听函数扫描之后
     * @return 所有的执行任务
     */
    protected Consumer<Class<?>[]>[] afterListener(CONFIG config, Application<CONFIG> app){
        return null;
    }

    /** 服务启动前 */
    protected void beforeStart(){ }

    /** 服务启动后 */
    protected void afterStart(){ }

    /**  监听函数注册之前，可以执行重写并进行额外的监听注入 */
    protected void beforeRegisterListener(CONFIG config, Application<CONFIG> app, ListenerMethodScanner scanner, DependCenter dependCenter) {

    }





    /**
     * 初始化
     */
    private void init(CONFIG config){
        //配置fastJson
        fastJsonInit();
        //公共资源初始化
        baseResourceInit();
        //线程工厂初始化
        threadPoolInit(config);
        //定时任务初始化
        timeTaskInit();
        //资源初始化
        resourceInit(config);
        //日志初始化
        logInit(config);
    }

    /**
     * 进行扫描
     */
    private Register scanner(Set<String> packages){
        //使用扫描管理器进行扫描
        return ScannerManager.scanner(packages);
    }

    /**
     * 配置结束后的方法
     */
    private DependCenter afterConfig(CONFIG config, Application<CONFIG> app){

        //构建监听扫描器
        ListenerMethodScanner scanner = ResourceDispatchCenter.getListenerMethodScanner();

        //包路径
        String appPackage = app.getClass().getPackage().getName();
        Set<String> scanAllPackage = new HashSet<>();

        //配置完成后，如果没有进行扫描，则默认扫描启动类同级包
        //需要扫描的包路径，如果是null则扫描启动器的根路径，否则按照要求进行扫描
        Set<String> scannerPackage = config.getScannerPackage();

        //查看启动类上是否存在@AllBeans注解
        AllBeans annotation = AnnotationUtils.getAnnotation(app.getClass(), AllBeans.class);
        if(annotation != null){
            //如果存在全局包扫描
            String[] value = annotation.value();
            if(value.length == 0){
                scanAllPackage.add(appPackage);
            }else{
                scanAllPackage = Arrays.stream(value).collect(Collectors.toSet());
            }

        }

        //包扫描路径，如果没有且类上没有全局搜索注解，则默认扫描启动类下包
        if((scannerPackage == null || scannerPackage.isEmpty())){
            scannerPackage = new HashSet<String>(){{
                add(appPackage);
            }};
        }

        //**************** 执行扫描 ****************//
        //进行扫描并保存注册器
        this.register = scanner(scannerPackage);

        //**************** 配置依赖注入相关 ****************//
        //配置依赖管理器
        //将依赖管理对象放入资源管理中心
        DependGetter dependGetter = getConfiguration().getDependGetter();

        //此处可以尝试去寻找被扫描到的接口对象
        // 寻找携带@Config且实现了Dependgetter的类
        if(dependGetter == null){
            dependGetter = register.performingTasks(
                    //过滤出携带者Config注解的、不是接口和抽象类的、是DependGetter的子类的
                    c -> (AnnotationUtils.getAnnotation(c, Config.class) != null) &&
                            (FieldUtils.notInterfaceAndAbstract(c)) && (FieldUtils.isChild(c, DependGetter.class)),
                    //看看有没有，如果有，赋值。
                    cs -> {
                if(cs.length == 1){
                    //找到一个，尝试实例化
                    Class<?> c = cs[0];
                    try {
                        return (DependGetter) BeansUtils.getInstance(c);
                    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                        return null;
                    }
                }else if(cs.length == 0){
                    return null;
                }else{
                    throw new RobotRuntimeException("扫描到多个" + DependGetter.class + "的实现类。");
                }
            });
        }

        // > 依赖扫描之前
        beforeDepend(config, app, register);


        DependCenter dependCenter = dependGetter == null ? new DependCenter() : new DependCenter(dependGetter);
        ResourceDispatchCenter.saveDependCenter(dependCenter);

        //赋值
        this.dependGetter = dependCenter;

        // ***** 注入一些其他的东西且无视异常 ***** //

        // 注入自己
        dependCenter.loadIgnoreThrow(dependCenter);
        // 注入CQCodeUtil
        dependCenter.loadIgnoreThrow(CQCodeUtil.build());
        // 注入当前这个启动器
        dependCenter.loadIgnoreThrow(this);
        // 注入配置类
        dependCenter.loadIgnoreThrow(config);




        //如果有全局注入，先扫描并注入全局注入
        if(annotation != null){
            //获取扫描器
            FileScanner fileScanner = new FileScanner();
            //扫描
            for (String p : scanAllPackage) {
                //全局扫描中，如果存在携带@beans的注解，则跳过.
                //全局扫描只能将不存在@Beans注解的依赖进行添加
                fileScanner.find(p, c -> AnnotationUtils.getBeansAnnotationIfListen(c) == null);
            }
            //获取扫描结果
            Set<Class<?>> classes = fileScanner.get();
            ScannerManager.getInstance(classes).registerDependCenterWithoutAnnotation(annotation.beans());
        }

        //注入依赖-普通的扫描依赖
        this.register.registerDependCenter(dependCenter);


        // > 依赖注入之后
        afterDepend(config, app, this.register, dependCenter);

        // > 监听函数注册之前
        beforeRegisterListener(config, app, scanner, dependCenter);

        //直接注册监听函数
        this.register.registerListener(scanner);


        // > 监听函数注册之后
        Consumer<Class<?>[]>[] afterListenerConsumer = afterListener(config, app);
        if(afterListenerConsumer != null){
            for (Consumer<Class<?>[]> c : afterListenerConsumer) {
                register.performingTasks(c);
            }
        }


        //根据配置类的扫描结果来构建监听器管理器和阻断器
        // 准备获取消息拦截器
        MsgIntercept[] msgIntercepts = register.performingTasks(
                c -> FieldUtils.isChild(c, MsgIntercept.class),
                (Class<?>[] cs) -> Arrays.stream(cs).map(dependCenter::get).toArray(MsgIntercept[]::new)
        );



        // 构建管理中心
        ListenerManager manager = scanner.buildManager(msgIntercepts);
        // 构建阻断器
        Plug plug = scanner.buildPlug();

        //保存
        ResourceDispatchCenter.saveListenerManager(manager);
        ResourceDispatchCenter.savePlug(plug);


        //返回依赖管理器
        return dependCenter;
    }

    /**
     * 有些事情需要连接之后才能做，例如加载定时任务，需要空函数送信器
     */
    private void after(){
        //注册监听函数
        this.register.registerTimeTask(this.NO_METHOD_SENDER);
    }

    /**
     * 执行的主程序
     */
    public void run(Application<CONFIG> app){
        //无配置资源初始化
        resourceInit();

        //获取配置对象
        CONFIG configuration = getConfiguration();

        //用户进行配置
        app.before(configuration);

        //初始化
        init(configuration);

        //配置结束, 获取依赖管理器
        DependCenter dependCenter = afterConfig(configuration, app);

        //获取管理器
        ListenerManager manager = ResourceDispatchCenter.getListenerManager();



        // > 启动之前
        beforeStart();



        //开始连接
        long s = System.currentTimeMillis();

        String name = start(dependCenter, manager);

        long e = System.currentTimeMillis();
        String msg = name + "启动成功,耗时(" + (e - s) + "ms)";
        QQLog.info(Colors.builder().add(msg, Colors.FONT.DARK_GREEN).build());



        // > 启动之后
        afterStart();



        //获取CQCodeUtil实例
        CQCodeUtil cqCodeUtil = ResourceDispatchCenter.getCQCodeUtil();
        //构建没有监听函数的送信器并保存
        MsgSender sender = MsgSender.build(getSender(), getSetter(), getGetter());
        this.NO_METHOD_SENDER = sender;
        // MsgSender存入依赖中心
        dependCenter.loadIgnoreThrow(sender);

        after();

        //连接之后
        app.after(cqCodeUtil, sender);
    }


    //**************** 部分资源获取API ****************//

    /**
     * 获取依赖获取器
     */
    public DependGetter getDependGetter(){
        return this.dependGetter;
    }

    /**
     * 获取空函数送信器<br>
     * ※ 此送信器无法进行阻断
     */
    public MsgSender getMsgSender(){
        return this.NO_METHOD_SENDER;
    }


    //**************** 构造 ****************//
    /** 无参构造 */
    public BaseApplication(){}

    /** 日志拦截构造 */
    public BaseApplication(QQLogBack qqLogBack){
        QQLog.changeQQLogBack(qqLogBack);
    }


}
