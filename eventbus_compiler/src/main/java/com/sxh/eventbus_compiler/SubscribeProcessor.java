package com.sxh.eventbus_compiler;

import com.google.auto.service.AutoService;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sxh.eventbus_annotation.EventBeans;
import com.sxh.eventbus_annotation.Subscribe;
import com.sxh.eventbus_annotation.SubscriberInfo;
import com.sxh.eventbus_annotation.SubscriberMethod;
import com.sxh.eventbus_annotation.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes({Constants.SUBSCRIBE_ANNOTATION_TYPES})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({Constants.PACKAGE_NAME, Constants.CLASS_NAME})
public class SubscribeProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    // APT包名
    private String packageName;

    // APT类名
    private String className;

    // 存储订阅方法信息   例如：key=MainActivity, value=MainActivity中订阅方法集合
    private final Map<TypeElement, List<ExecutableElement>> subscribeMethodsInClass = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // 初始化
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        messager = processingEnvironment.getMessager();
        filer = processingEnvironment.getFiler();

        //获取参数PACKAGE_NAME、CLASS_NAME
        Map<String, String> options = processingEnvironment.getOptions();
        if (!Utils.isEmpty(options)) {
            packageName = options.get(Constants.PACKAGE_NAME);
            className = options.get(Constants.CLASS_NAME);
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "packageName >>> " + packageName + " / className >>> " + className);
        }

        // 必传参数判空（乱码问题：添加java控制台输出中文乱码）
        if (Utils.isEmpty(packageName) || Utils.isEmpty(className)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "参数为空，请前去build.gradle配置参数");
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (!Utils.isEmpty(set)) {
            //获取所有被@Subscribe注解的元素集合
            Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(Subscribe.class);

            if (!Utils.isEmpty(elements)) {
                try {
                    //解析Element
                    parseElements(elements);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 解析被@Subscribe注解修饰Element
     *
     * @param elements
     * @throws IOException
     */
    private void parseElements(Set<? extends Element> elements) throws IOException {
        //遍历节点
        for (Element element : elements) {
            //@Subscribe注解只能在方法之上
            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Subscribe注解只能在方法上使用");
                return;
            }
            //强转方法元素
            ExecutableElement method = (ExecutableElement) element;
            //方法检查
            if (checkHasNoErrors(method)) {
                //获取封装订阅方法的类，比如MainActivity
                TypeElement classElement = (TypeElement) method.getEnclosingElement();

                // 以类名为key，保存订阅方法
                List<ExecutableElement> methods = subscribeMethodsInClass.get(classElement);
                if (methods == null) {
                    methods = new ArrayList<>();
                    subscribeMethodsInClass.put(classElement, methods);
                }
                methods.add(method);
            }
            messager.printMessage(Diagnostic.Kind.NOTE, "遍历注解方法：" + method.getSimpleName().toString());
        }

        // 通过Element工具类，获取SubscriberInfoIndex类型
        TypeElement subscriberIndexType = elementUtils.getTypeElement(Constants.I_SUBSCRIBERINFO_INDEX);

        // 生成类文件
        createFile(subscriberIndexType);
    }

    private void createFile(TypeElement subscriberIndexType) throws IOException {
        // 添加静态块代码：SUBSCRIBER_INDEX = new HashMap<Class, SubscriberInfo>();
        CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.addStatement("$N = new $T<$T, $T>()",
                Constants.FIELD_NAME,
                HashMap.class,
                Class.class,
                SubscriberInfo.class);

        // 双层循环，第一层遍历被@Subscribe注解的方法所属类。第二层遍历每个类中所有订阅的方法
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : subscribeMethodsInClass.entrySet()) {

            CodeBlock.Builder contentBlock = CodeBlock.builder();
            CodeBlock contentCode = null;
            String format;
            for (int i = 0; i < entry.getValue().size(); i++) {
                // 获取每个方法上的@Subscribe注解中的注解值
                Subscribe subscribe = entry.getValue().get(i).getAnnotation(Subscribe.class);
                // 获取订阅事件方法所有参数
                List<? extends VariableElement> parameters = entry.getValue().get(i).getParameters();
                // 获取订阅事件方法名
                String methodName = entry.getValue().get(i).getSimpleName().toString();
                // 注意：此处还可以做检查工作，比如：参数类型必须是类或接口类型（这里缩减了）
                TypeElement parameterElement = (TypeElement) typeUtils.asElement(parameters.get(0).asType());
                // 如果是最后一个添加，则无需逗号结尾
                if (i == entry.getValue().size() - 1) {
                    format = "new $T($T.class, $S, $T.class, $T.$L, $L)";
                } else {
                    format = "new $T($T.class, $S, $T.class, $T.$L, $L),\n";
                }
                //生成 new SubscriberMethod(MainActivity.class, "onEvent", LoginEvent.class, ThreadMode.POSTING, false)
                contentCode = contentBlock.add(format,
                        SubscriberMethod.class,
                        ClassName.get(entry.getKey()),
                        methodName,
                        ClassName.get(parameterElement),
                        ThreadMode.class,
                        subscribe.threadMode(),
                        subscribe.sticky())
                        .build();
            }

            if (contentCode != null) {
                // putItem(new EventBeans(MainActivity.class, new SubscriberMethod[] {)
                codeBlock.beginControlFlow(Constants.PUTITEM_METHOD_NAME + "(new $T($T.class, new $T[]",
                        EventBeans.class,
                        ClassName.get(entry.getKey()),
                        SubscriberMethod.class)
                        .add(contentCode)
                        .endControlFlow("))");
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "注解处理器双层循环发生错误！");
            }
        }

        // 全局属性：Map<Class<?>, SubscriberMethod>
        TypeName fieldType = ParameterizedTypeName.get(
                ClassName.get(Map.class), // Map
                ClassName.get(Class.class), // Map<Class,
                ClassName.get(SubscriberInfo.class) // Map<Class, SubscriberMethod>
        );

        // putItem方法参数：putItem(SubscriberInfo subscriberInfo)
        ParameterSpec putIndexParameter = ParameterSpec.builder(
                ClassName.get(SubscriberInfo.class),
                Constants.PUTITEM_PARAMETER_NAME)
                .build();

        // putItem方法配置：private static void putItem(SubscriberInfo subscriberInfo) {
        MethodSpec.Builder putIndexBuidler = MethodSpec
                .methodBuilder(Constants.PUTITEM_METHOD_NAME)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(putIndexParameter)
                .returns(void.class);

        // putItem方法内容：SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);
        putIndexBuidler.addStatement("$N.put($N.getSubscriberClass(), $N)",
                Constants.FIELD_NAME,
                Constants.PUTITEM_PARAMETER_NAME,
                Constants.PUTITEM_PARAMETER_NAME);

        // getSubscriberInfo方法参数：Class subscriberClass
        ParameterSpec getSubscriberInfoParameter = ParameterSpec.builder(
                ClassName.get(Class.class),
                Constants.GETSUBSCRIBERINFO_PARAMETER_NAME)
                .build();

        // getSubscriberInfo方法配置：public SubscriberMethod getSubscriberInfo(Class<?> subscriberClass) {
        MethodSpec.Builder getSubscriberInfoBuidler = MethodSpec
                .methodBuilder(Constants.GETSUBSCRIBERINFO_METHOD_NAME) // 方法名
                .addAnnotation(Override.class) // 重写方法注解
                .addModifiers(Modifier.PUBLIC) // public修饰符
                .addParameter(getSubscriberInfoParameter) // 方法参数
                .returns(SubscriberInfo.class); // 方法返回值

        // getSubscriberInfo方法内容：return SUBSCRIBER_INDEX.get(subscriberClass);
        getSubscriberInfoBuidler.addStatement("return $N.get($N)",
                Constants.FIELD_NAME,
                Constants.GETSUBSCRIBERINFO_PARAMETER_NAME);

        // 构建类
        TypeSpec typeSpec = TypeSpec.classBuilder(className)
                // 实现SubscriberInfoIndex接口
                .addSuperinterface(ClassName.get(subscriberIndexType))
                // 该类的修饰符
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                // 添加静态块（很少用的api）
                .addStaticBlock(codeBlock.build())
                // 全局属性：private static final Map<Class<?>, SubscriberMethod> SUBSCRIBER_INDEX
                .addField(fieldType, Constants.FIELD_NAME, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                // 第一个方法：加入全局Map集合
                .addMethod(putIndexBuidler.build())
                // 第二个方法：通过订阅者对象（MainActivity.class）获取所有订阅方法
                .addMethod(getSubscriberInfoBuidler.build())
                .build();

        // 生成类文件：EventBusIndex
        JavaFile.builder(packageName,
                typeSpec)
                .build()
                .writeTo(filer);
    }

    /**
     * 方法相关检查
     *
     * @param element 方法元素
     * @return 检查是否通过
     */
    private boolean checkHasNoErrors(ExecutableElement element) {
        // 不能为static静态方法
        if (element.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法不能是static静态方法", element);
            return false;
        }

        // 必须是public修饰的方法
        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法必须是public修饰的方法", element);
            return false;
        }

        // 订阅事件方法必须只有一个参数
        List<? extends VariableElement> parameters = ((ExecutableElement) element).getParameters();
        if (parameters.size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法有且仅有一个参数", element);
            return false;
        }
        return true;
    }

}
