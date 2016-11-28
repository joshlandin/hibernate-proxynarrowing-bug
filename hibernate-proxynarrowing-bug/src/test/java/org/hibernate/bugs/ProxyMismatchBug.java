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
import org.hibernate.Transaction;
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
 * This template demonstrates how to develop a test case for Hibernate ORM, using its built-in unit test framework.
 * Although ORMStandaloneTestCase is perfectly acceptable as a reproducer, usage of this class is much preferred.
 * Since we nearly always include a regression test with bug fixes, providing your reproducer using this method
 * simplifies the process.
 *
 * What's even better?  Fork hibernate-orm itself, add your test case directly to a module's unit tests, then
 * submit it as a PR!
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
  public void hhh123Test() throws Exception {
      // BaseCoreFunctionalTestCase automatically creates the SessionFactory and provides the Session.
      Session s = openSession();
      Transaction tx = s.beginTransaction();
      
      {
        DetachedCriteria criteria = DetachedCriteria.forClass(UserConfig.class);
        criteria.add(Restrictions.eq("id", new Long(10)));
        final List<UserConfig> rows = criteria.getExecutableCriteria(s).list();
        UserConfig config = rows.get(0);
        User u = config.getUser();
        Assert.assertNotNull(u);
      }

      try
      {
        DetachedCriteria criteria = DetachedCriteria.forClass(User.class);
        criteria.add(Restrictions.eq("id", new Long(11)));
        final List<User> rows = criteria.getExecutableCriteria(s).list();
        User u = rows.get(0);
        Assert.assertEquals(106, u.getType().intValue());
        AdvancedUser a = (AdvancedUser)u;
        Assert.assertNotNull(a);
      }
      catch(ClassCastException e)
      {
        Assert.fail(String.format("The User proxy is not an AdvancedUser instance but should be (HHH-9071). Hibernate version detected as '%s'. %s", Version.getVersionString(), e.getMessage()));
      }

      tx.commit();
      s.close();
  }

}