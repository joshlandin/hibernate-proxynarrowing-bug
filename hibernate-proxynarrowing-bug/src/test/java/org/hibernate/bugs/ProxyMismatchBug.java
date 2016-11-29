/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.bugs;

import java.util.List;

import junit.framework.Assert;

import org.hibernate.Session;
import org.hibernate.Version;
import org.hibernate.bugs.model.AdvancedUser;
import org.hibernate.bugs.model.AdvancedUserDetail;
import org.hibernate.bugs.model.User;
import org.hibernate.bugs.model.UserConfig;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for HHH11280 - Proxy Narrowing (HHH-9071) breaks polymorphic query.
 * 
 * @jlandin
 */
public class ProxyMismatchBug extends BaseCoreFunctionalTestCase {

  // Add your entities here.
  @Override
  protected Class[] getAnnotatedClasses() {
      return new Class[] {
      };
  }

  @Override
  protected String[] getMappings() {
      return new String[] {
                "Models.hbm.xml",
      };
  }
  // If those mappings reside somewhere other than resources/org/hibernate/test, change this.
  @Override
  protected String getBaseForMappings() {
      return "org/hibernate/bugs/model/";
  }
  
  // Add in any settings that are specific to your test.  See resources/hibernate.properties for the defaults.
  @Override
  protected void configure(Configuration configuration) {
      super.configure( configuration );

      configuration.setProperty( AvailableSettings.SHOW_SQL, Boolean.TRUE.toString() );
      configuration.setProperty( AvailableSettings.FORMAT_SQL, Boolean.TRUE.toString() );
      
      configuration.setProperty( AvailableSettings.DIALECT, "org.hibernate.dialect.MySQL5Dialect" );
      configuration.setProperty( AvailableSettings.DRIVER, "com.mysql.jdbc.Driver");
      configuration.setProperty( AvailableSettings.URL, "jdbc:mysql://localhost:3306/hib-test");
      configuration.setProperty( AvailableSettings.USER, "hib");
      configuration.setProperty( AvailableSettings.PASS, "hib");
  }
  
  @Before
  public void setUpData() {
      Session s = openSession();
      s.beginTransaction();

      UserConfig config = new UserConfig();
      AdvancedUserDetail detail = new AdvancedUserDetail();
      AdvancedUser advUser = new AdvancedUser();

      detail.setId(9L);
      detail.setStatusCode("A");
      detail.setAdvancedUser(advUser);
      
      advUser.setId(11L);
      advUser.setType(106);
      advUser.setCurrentDetail(detail);
      
      config.setId(10L);
      config.setUser(advUser);
      
      s.persist(advUser);
      s.persist(detail);
      s.persist(config);
      s.getTransaction().commit();
      s.close();
  }


  @Test
  public void testHHH11280() throws Exception {
      Session s = openSession();
      
      DetachedCriteria configCriteria = DetachedCriteria.forClass(UserConfig.class);
      configCriteria.add(Restrictions.eq("id", new Long(10)));
      final List<UserConfig> configRows = configCriteria.getExecutableCriteria(s).list();
      UserConfig config = configRows.get(0);
      User configUser = config.getUser();
      Assert.assertNotNull(configUser);
      
      // Evicting the proxy instance from the session cache would 
      // let the test pass, but this is not a practical solution in 
      // real applications.
      // --
      // s.evict(u); 

      try
      {
        DetachedCriteria userCriteria = DetachedCriteria.forClass(User.class);
        userCriteria.add(Restrictions.eq("id", new Long(11)));
        final List<User> userRows = userCriteria.getExecutableCriteria(s).list();
        User user = userRows.get(0);
        Assert.assertEquals(106, user.getType().intValue());
        AdvancedUser advancedUser = (AdvancedUser)user;
        Assert.assertNotNull(advancedUser);
      }
      catch(ClassCastException e)
      {
        Assert.fail(String.format("The User proxy is not an AdvancedUser instance but should be (HHH-9071). Hibernate version detected as '%s'. %s", Version.getVersionString(), e.getMessage()));
      }

      s.close();
  }

}