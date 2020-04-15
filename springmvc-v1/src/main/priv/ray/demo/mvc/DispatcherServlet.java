package priv.ray.demo.mvc;

import priv.ray.demo.mvc.annotation.Autowired;
import priv.ray.demo.mvc.annotation.Controller;
import priv.ray.demo.mvc.annotation.RequestMapping;
import priv.ray.demo.mvc.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {

    // 保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();
    // 保存所有扫描到的类名
    private List<String> classNameList = new ArrayList<>();
    // IOC容器
    private Map<String, Object> ioc = new HashMap<>();
    // 保存url和method对应关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackages"));
        System.out.println(classNameList);
        // 3、初始化扫描到的类，并且将他们放入到IOC容器之中
        doInstance();

        // 4、完成依赖注入
        doAutowired();

        // 5、初始化HandlerMapping
        initHandlerMapping();

        System.out.println("springframework is init");
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackages) {
        // classPath
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackages.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackages + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = scanPackages + "." + file.getName().replace(".class", "");
                classNameList.add(className);
            }
        }
    }

    private void doInstance() {
        if (classNameList.isEmpty()) {
            return;
        }

        try {
            for (String className : classNameList) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(Controller.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if (!"".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("this " + i.getName() + " is already exist");
                        }
                        ioc.put(i.getName(), instance);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doAutowired() {
        if (ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // Declared
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) continue;

                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // 初始化url和Method的对应关系
    private void initHandlerMapping() {
        if (ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(Controller.class)) continue;

            // 类上面的url
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(RequestMapping.class)) continue;

                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
            }

        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        System.out.println(url);
        System.out.println(handlerMapping);
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!");
        }

        Method method = this.handlerMapping.get(url);
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(ioc.get(beanName), req, resp, params.get("name")[0]);
    }

}
