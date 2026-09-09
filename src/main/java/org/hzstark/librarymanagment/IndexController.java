package org.hzstark.librarymanagment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class IndexController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private BookRepository bookRepository;

    private boolean isRole(HttpSession session, String role) {
        String sessRole = (String) session.getAttribute("role");
        return sessRole != null && sessRole.equals(role);
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (isRole(session, "MEMBER")) return "redirect:/user_panel";
        if (isRole(session, "ADMIN")) return "redirect:/admin_panel";
        return "index.html";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String userType,
            @RequestParam(name="remember-me", required = false) String rememberMe,
            HttpServletRequest request
    ) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        
        HttpSession newSession = request.getSession(true);
        int maxAge = (rememberMe != null) ? (30 * 24 * 60 * 60) : -1;
        newSession.setMaxInactiveInterval(maxAge);

        try {
            if ("member".equals(userType)) {
                List<User> users = userRepository.findByEmail(email);
                if (!users.isEmpty()) {
                    User user = users.get(0);
                    if (user.getPassword() != null && user.getPassword().startsWith("$2a$") && BCrypt.checkpw(password, user.getPassword())) {
                        newSession.setAttribute("role", "MEMBER");
                        newSession.setAttribute("userId", user.getId());
                        newSession.setAttribute("isTemp", rememberMe == null);
                        return "redirect:/user_panel";
                    }
                }
            } else if ("admin".equals(userType)) {
                List<Admin> admins = adminRepository.findByEmail(email);
                if (!admins.isEmpty()) {
                    Admin admin = admins.get(0);
                    if (admin.getPassword() != null && admin.getPassword().startsWith("$2a$") && BCrypt.checkpw(password, admin.getPassword())) {
                        newSession.setAttribute("role", "ADMIN");
                        newSession.setAttribute("userId", admin.getId());
                        newSession.setAttribute("isTemp", rememberMe == null);
                        return "redirect:/admin_panel";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/?error=true";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();       
        }
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register.html";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password
    ) {
        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userRepository.save(newUser);
        return "redirect:/";
    }

    @GetMapping("/user_panel")
    public String userPanel(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String error,
            HttpSession session,
            Model model
    ) {
        if (!isRole(session, "MEMBER")) {
            return "redirect:/";
        }
        
        Long userId = (Long) session.getAttribute("userId");
        Boolean isTemp = (Boolean) session.getAttribute("isTemp");
        
        Optional<User> uOpt = userRepository.findById(userId);
        if (uOpt.isEmpty()) return "redirect:/logout";
        
        model.addAttribute("user", uOpt.get());
        model.addAttribute("isTempSession", Boolean.TRUE.equals(isTemp));
        model.addAttribute("error", error);

        List<Book> userBooks = bookRepository.findByBorrowerId(userId);
        if (!userBooks.isEmpty()) {
            model.addAttribute("activeBook", userBooks.get(0));
        }

        if (q != null && !q.trim().isEmpty()) {
            model.addAttribute("books", bookRepository.searchByNameOrId(q.trim()));
            model.addAttribute("query", q.trim());
        } else {
            model.addAttribute("books", bookRepository.findAll());
        }
        
        return "user_panel.html";
    }

    @PostMapping("/user/book/request")
    public String requestBook(
            @RequestParam Long id,
            HttpSession session
    ) {
        if (!isRole(session, "MEMBER")) return "redirect:/";
        Long userId = (Long) session.getAttribute("userId");
        
        List<Book> userBooks = bookRepository.findByBorrowerId(userId);
        if (!userBooks.isEmpty()) {
            return "redirect:/user_panel?error=already_borrowed";
        }

        Optional<Book> bOpt = bookRepository.findById(id);
        if (bOpt.isPresent()) {
            Book book = bOpt.get();
            if ("AVAILABLE".equals(book.getStatus())) {
                book.setStatus("REQUESTED");
                book.setBorrowerId(userId);
                bookRepository.save(book);
            }
        }
        return "redirect:/user_panel";
    }

    @GetMapping("/admin_panel")
    public String adminPanel(
            HttpSession session,
            Model model
    ) {
        if (!isRole(session, "ADMIN")) {
            return "redirect:/";
        }
        
        Long adminId = (Long) session.getAttribute("userId");
        Boolean isTemp = (Boolean) session.getAttribute("isTemp");
        
        Optional<Admin> aOpt = adminRepository.findById(adminId);
        if (aOpt.isEmpty()) return "redirect:/logout";
        
        List<User> members = userRepository.findAll();
        Map<Long, String> userNames = new HashMap<>();
        for (User u : members) {
            userNames.put(u.getId(), u.getName());
        }

        model.addAttribute("admin", aOpt.get());
        model.addAttribute("isTempSession", Boolean.TRUE.equals(isTemp));
        model.addAttribute("books", bookRepository.findAll());
        model.addAttribute("members", members); 
        model.addAttribute("userNames", userNames);
        model.addAttribute("requestedBooks", bookRepository.findByStatus("REQUESTED"));
        model.addAttribute("borrowedBooks", bookRepository.findByStatus("BORROWED"));
        
        return "admin_panel.html";
    }

    @PostMapping("/admin/book/add")
    public String addBook(
            @RequestParam String name,
            @RequestParam Integer pageCount,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        
        Book book = new Book();
        book.setName(name);
        book.setPageCount(pageCount);
        book.setStatus("AVAILABLE");
        bookRepository.save(book);
        
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/book/delete")
    public String deleteBook(
            @RequestParam Long id,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        bookRepository.deleteById(id);
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/book/approve")
    public String approveBook(
            @RequestParam Long id,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        Optional<Book> bOpt = bookRepository.findById(id);
        if (bOpt.isPresent() && "REQUESTED".equals(bOpt.get().getStatus())) {
            Book book = bOpt.get();
            book.setStatus("BORROWED");
            bookRepository.save(book);
        }
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/book/return")
    public String returnBook(
            @RequestParam Long id,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        Optional<Book> bOpt = bookRepository.findById(id);
        if (bOpt.isPresent()) {
            Book book = bOpt.get();
            book.setStatus("AVAILABLE");
            book.setBorrowerId(null);
            bookRepository.save(book);
        }
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/member/add")
    public String addMember(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userRepository.save(user);
        
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/member/delete")
    public String deleteMember(
            @RequestParam Long id,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        userRepository.deleteById(id);
        return "redirect:/admin_panel";
    }

    @PostMapping("/admin/member/password")
    public String changeMemberPassword(
            @RequestParam Long id,
            @RequestParam String newPassword,
            HttpSession session
    ) {
        if (!isRole(session, "ADMIN")) return "redirect:/";
        
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isPresent()) {
            User user = uOpt.get();
            user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
            userRepository.save(user);
        }
        return "redirect:/admin_panel";
    }
}
