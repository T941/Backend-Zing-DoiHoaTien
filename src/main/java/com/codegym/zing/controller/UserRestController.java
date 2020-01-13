package com.codegym.zing.controller;

import com.codegym.zing.model.JwtResponse;
import com.codegym.zing.model.Role;
import com.codegym.zing.model.User;
import com.codegym.zing.model.VerificationToken;
import com.codegym.zing.service.PlaylistService;
import com.codegym.zing.service.RoleService;
import com.codegym.zing.service.UserService;
import com.codegym.zing.service.VerificationTokenService;
import com.codegym.zing.service.impl.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin("*")
public class UserRestController {
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private Environment env;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    private Long playlistId;

    @ModelAttribute("userCurent")
    public User getUserCurent(){
        return userService.getCurrentUser();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<User> findById(@PathVariable Long userId){
        User user = userService.findById(userId);
        if (user == null){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> listUser(@RequestParam("username") Optional<String> username){
        List<User> users = new ArrayList<>();
        if (username.isPresent()){
            User user = userService.findByUsername(username.get());
            if (user == null){
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            users.add(user);
        }
        users = userService.findAll();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<User> createUser(@Valid @RequestBody User user, BindingResult bindingResult) {
        if (bindingResult.hasFieldErrors()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (!userService.isCorrectConfirmPassword(user)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Role role = user.getRoles();
        if (roleService.findRoleByName(role.getName()) == null){
            roleService.save(role);
        } else role = roleService.findRoleByName(role.getName());
        user.setRoles(role);
        Iterable<User> users = userService.findAll();
        for (User currentUser : users) {
            if (currentUser.getUsername().equals(user.getUsername())) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setConfirmPassword(passwordEncoder.encode(user.getConfirmPassword()));
        userService.save(user);
        VerificationToken token = new VerificationToken(user);
        token.setExpiryDate(10);
        verificationTokenService.save(token);
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtService.generateTokenLogin(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), userDetails.getAuthorities()));
    }

    @RequestMapping(value = "/new-password/{id}", method = {RequestMethod.POST})
    public ResponseEntity<User> updatePassword(@PathVariable Long id, @RequestBody User user) {
        Optional<User> userOptional = Optional.ofNullable(userService.findById(id));
        if (!userOptional.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!userService.isCorrectConfirmPassword(user)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String newPassword = passwordEncoder.encode(user.getPassword());
        String confirmPassword = passwordEncoder.encode(user.getConfirmPassword());
        user.setPassword(newPassword);
        user.setConfirmPassword(confirmPassword);
        userService.save(user);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @GetMapping("/userCurrent")
    public ResponseEntity<User> userCurrent(){
        User user = userService.getCurrentUser();
        return new ResponseEntity<>(user, HttpStatus.OK);
    }
}
