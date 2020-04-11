package priv.ray.demo.mvc.controller;

import priv.ray.demo.mvc.annotation.Autowired;
import priv.ray.demo.mvc.annotation.Controller;
import priv.ray.demo.mvc.annotation.RequestMapping;
import priv.ray.demo.mvc.service.IOneService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/demo")
public class OneController {

    @Autowired
    private IOneService oneService;

    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, String name) {
        try {
            resp.getWriter().write(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
