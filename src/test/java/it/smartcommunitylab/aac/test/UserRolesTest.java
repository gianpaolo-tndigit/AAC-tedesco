package it.smartcommunitylab.aac.test;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.Config.ROLE_SCOPE;
import it.smartcommunitylab.aac.manager.RegistrationManager;
import it.smartcommunitylab.aac.manager.RoleManager;
import it.smartcommunitylab.aac.model.Role;
import it.smartcommunitylab.aac.model.User;
import it.smartcommunitylab.aac.repository.RegistrationRepository;
import it.smartcommunitylab.aac.repository.UserRepository;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
public class UserRolesTest {

	private static final String TESTAPP = "testapp";
	private static final String USERNAME = "testuser";
	private static final String USERNAME2 = "testuser2";
	private static final String CUSTOM_ROLE = "ROLE_CUSTOM";
	private static final String CUSTOM_ROLE_VALUE = "value";

	@Autowired
	private RegistrationManager registrationManager;	
	
	@Autowired
	private RoleManager roleManager;		
	@Autowired
	private RegistrationManager regManager;		
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private RegistrationRepository registrationRepository;		

	private Role role1 = new Role(ROLE_SCOPE.system, Config.R_ADMIN, null);
	private Role role2 = new Role(ROLE_SCOPE.application, Config.R_USER, TESTAPP);
	private Role role3 = new Role(ROLE_SCOPE.user, Config.R_ADMIN, TESTAPP);
	private Role role4 = new Role(ROLE_SCOPE.tenant, CUSTOM_ROLE, CUSTOM_ROLE_VALUE);
	private User user;
	
	@Before
	public void createUser() {
		user = registrationManager.registerOffline("NAME", "SURNAME", USERNAME, "password", null, false, null);
		user.setRoles(Sets.newHashSet(role1));
		userRepository.save(user);		
	}
	
	@After
	public void deleteUser() {
		registrationRepository.deleteAll();
		userRepository.deleteAll();
	}	
	
	@Test
	public void testNewRoles() {
		
		User created = regManager.registerOffline(USERNAME2, USERNAME2, USERNAME2, USERNAME2, Config.DEFAULT_LANG, false, null);
		Role providerRole = new Role(ROLE_SCOPE.tenant, CUSTOM_ROLE, CUSTOM_ROLE_VALUE);
		roleManager.addRole(created, providerRole);
		
		Assert.assertTrue(roleManager.hasRole(created, role4));
	}
	
	@Test
	public void testChangeRoles() {
		Long id = user.getId();
		
		user = userRepository.findOne(id);
		
		Assert.assertTrue(roleManager.hasRole(user, role1));

		roleManager.removeRole(user, role1);
		roleManager.addRole(user, role2);
		
		user = userRepository.findOne(id);
		
		Assert.assertFalse(roleManager.hasRole(user, role1));
		Assert.assertTrue(roleManager.hasRole(user, role2));
		
		roleManager.updateRoles(user, Sets.newHashSet(role1));
		
		user = userRepository.findOne(id);
		
		Assert.assertTrue(roleManager.hasRole(user, role1));
		Assert.assertFalse(roleManager.hasRole(user, role2));		
	}
	
	@Test
	public void testFindByRole() {
//		Pageable pageable = new PageRequest(0, 5);
//		List<User> users = userRepository.findByPartialRole(role1.getRole(), role1.getScope(), pageable);
		List<User> users = roleManager.findUsersByRole(role1.getScope(), role1.getRole(), 0, 5);
		Assert.assertEquals(2, users.size());
		
//		pageable = new PageRequest(0, 1);
//		users = userRepository.findByPartialRole(role1.getRole(), role1.getScope(), pageable);
		users = roleManager.findUsersByRole(role1.getScope(), role1.getRole(), 0, 1);
		Assert.assertEquals(1, users.size());		
		
//		pageable = new PageRequest(1, 1);
//		users = userRepository.findByPartialRole(role1.getRole(), role1.getScope(), pageable);
		users = roleManager.findUsersByRole(role1.getScope(), role1.getRole(), 1, 1);
		Assert.assertEquals(1, users.size());		
		
		roleManager.addRole(user, role2);
//		pageable = new PageRequest(0, 5);
//		users = userRepository.findByFullRole(role2.getRole(), role2.getScope(), TESTAPP, pageable);
		users = roleManager.findUsersByRole(role2.getScope(), role2.getRole(), role2.getContext(), 0, 5);
		Assert.assertEquals(1, users.size());	
		
//		users = userRepository.findByFullRole(role1.getRole(), role2.getScope(), TESTAPP, pageable);
		users = roleManager.findUsersByRole(role3.getScope(), role3.getRole(), role3.getContext(), 0, 5);
		Assert.assertEquals(0, users.size());			
		
	}
	
	
}