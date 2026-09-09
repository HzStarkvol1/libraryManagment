package org.hzstark.librarymanagment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController
{
    @GetMapping("/welcome")
    public String welcomePage()
    {
        System.out.println("kullanici anasayfaya geldi!");
        return "Welcome!";
    }
    @GetMapping("/tes")
    public String test()
    {
        System.out.println("kullanici test sayfasina geldi!");
        return "test";
    }
}
