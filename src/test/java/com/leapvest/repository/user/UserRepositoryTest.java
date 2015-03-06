package com.leapvest.repository.user;

import com.leapvest.TestContext;
import com.leapvest.model.person.*;
import com.leapvest.model.user.Account;
import com.leapvest.model.user.User;
import com.leapvest.repository.person.CountryRepository;
import com.leapvest.repository.person.PersonRepository;
import com.leapvest.repository.person.PostalCodeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.util.*;

import static org.fest.assertions.api.Assertions.assertThat;

@ContextConfiguration(classes = { TestContext.class })
public class UserRepositoryTest
    extends AbstractTestNGSpringContextTests {

    @Inject
    private UserRepository userRepository;

    @Inject
    private PersonRepository personRepository;

    @Inject
    private CountryRepository countryRepository;

    @Inject
    private PostalCodeRepository postalCodeRepository;

    @Test
    public void shouldTestCreation()
        throws Exception {

        userRepository.deleteAll();
        personRepository.deleteAll();

        User user;
        
        user = new User("alice");
        user.setPassword("abc1234");
        userRepository.save(user);

        user = new User("bob");
        user.setPassword("xyz0987");
        assertThat(user.isRegistered()).isFalse();
        userRepository.save(user);

        user = new User("jane");
        user.setPassword("ghi3456");

        Person owner = new Person("Doe", "Jane", "G", "Ms", "M.D.");
        
        owner.addContact(new Email("jane.doe@gmail.com"), true);
        owner.addContact(new Email("jane.doe@yahoo.com"));
        owner.addContact(new Email("jane.doe@msn.com"), true); // should override
        
        Country us = countryRepository.findByAlpha2Code("US");

        owner.addContact(new Phone(Phone.Type.land, "508-654-8675", us), true);
        owner.addContact(new Phone(Phone.Type.mobile, "978-545-4563", us));
        owner.addContact(new Phone(Phone.Type.office, "603-563-6565", us));

        PostalCode zip01581 = postalCodeRepository.readOne(new PostalCode(us, "01581"));
        PostalCode zip01721 = postalCodeRepository.readOne(new PostalCode(us, "01721"));

        owner.addContact(new PostalAddress(new String[]{"456 Main St"}, zip01581, us));
        owner.addContact(new PostalAddress(new String[]{"Apt #1234", "1900 Worcester Rd"}, zip01721, us), true);

        personRepository.save(owner);

        user.getAccount().addPrimaryOwner(owner);
        userRepository.save(user);
    }

    @Test(dependsOnMethods = { "shouldTestCreation" }, expectedExceptions = DuplicateKeyException.class)
    public void shouldTestUniqueness() {
        
        User user = new User("alice");
        user.setPassword("abcdefg");
        userRepository.save(user);
    }

    @Test(dependsOnMethods = { "shouldTestUniqueness" })
    public void shouldTestReading() {

        User alice1 = userRepository.findByLoginName("alice");
        assertThat(User.PASSWORD_ENCODER.matches("abc1234", alice1.getPassword())).isTrue();

        User alice2 = userRepository.findByLoginName("alice");
        assertThat(alice2).isEqualTo(alice1);

        User bob = userRepository.findByLoginName("bob");
        assertThat(bob).isNotEqualTo(alice1);

        User jane = userRepository.findByLoginName("jane");
        assertThat(jane.getAccount().getPrimaryOwner().getFormalName()).isEqualTo("Ms. Doe, Jane G., M.D.");

        Person person = jane.getAccount().getPrimaryOwner();
        
        Email email = person.getPrimaryEmail();
        assertThat(email.toString()).isEqualTo("jane.doe@msn.com");
        
        PostalAddress address = person.getPrimaryPostalAddress();
        assertThat(address.toString()).isEqualTo("Apt #1234, 1900 Worcester Rd, Ashland, MA 01721, United States of America");
        
        Phone phone = person.getPrimaryPhone();
        assertThat(phone.toString()).isEqualTo("(1) 508-654-8675 (land)");

        assertThat(person.getContacts(Email.class).size()).isEqualTo(3);
        assertThat(person.getContacts(Phone.class).size()).isEqualTo(3);
        assertThat(person.getContacts(PostalAddress.class).size()).isEqualTo(2);

        Collection<Contact> phoneNumbers = person.getContacts(Phone.class);
        Optional<Contact> mobile = phoneNumbers.stream().filter(c -> ((Phone) c).getType() == Phone.Type.mobile).findFirst();
        assertThat(mobile.isPresent()).isTrue();
        assertThat(mobile.get().toString()).isEqualTo("(1) 978-545-4563 (mobile)");
    }

    @Test(dependsOnMethods = { "shouldTestUniqueness" })
    public void shouldTestUpdate()
        throws Exception {

        User bob = userRepository.findByLoginName("bob");
        Person owner = new Person("Dylan", "Thomas", "Tyle", "Mr");

        Country us = countryRepository.findByAlpha2Code("US");
        PostalCode zip90210 = postalCodeRepository.readOne(new PostalCode(us, "90210"));

        owner.addContact(new Email("bdylan@gmail.com"));
        owner.addContact(new Phone(Phone.Type.mobile, "978-343-5555", us));
        owner.addContact(new PostalAddress(new String[]{"666 Fancy Lane"}, zip90210, us));
        personRepository.save(owner);

        bob.getAccount().addPrimaryOwner(owner);
        userRepository.save(bob);
        
        User bobdylan = userRepository.findByLoginName("bob");
        Person person = bobdylan.getAccount().getPrimaryOwner();

        Phone phone = person.getPrimaryPhone();
        assertThat(phone.toString()).isEqualTo("(1) 978-343-5555 (mobile)");

        User jane = userRepository.findByLoginName("jane");
        jane.getAccount().addPrimaryOwner(owner);
        userRepository.save(jane);
        
        User janetest = userRepository.findByLoginName("jane");
        assertThat(jane.getAccount().getPrimaryOwner().getFormalName()).isEqualTo("Mr. Dylan, Thomas Tyle");

        List<Person> owners = jane.getAccount().getOwners();
        janetest.getAccount().addPrimaryOwner(owners.get(1));
        userRepository.save(janetest);

        janetest = userRepository.findByLoginName("jane");
        assertThat(janetest.getAccount().getPrimaryOwner().getFormalName()).isEqualTo("Ms. Doe, Jane G., M.D.");
    }

    @Test(dependsOnMethods = { "shouldTestReading" })
    public void shouldTestDeletion() {

        User alice = userRepository.findByLoginName("alice");
        userRepository.delete(alice);
        
        alice = userRepository.findByLoginName("alice");
        assertThat(alice).isNull();
    }
}
