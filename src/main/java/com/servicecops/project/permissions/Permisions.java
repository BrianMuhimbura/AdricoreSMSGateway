package com.servicecops.project.permissions;

import com.servicecops.project.models.jpahelpers.enums.AppDomains;

/**
 * This class will hold all our permissions we are using in the system
 *
 * It comes with an example of how to register a new permission.
 *
 * NOTE:- If your app does not follow the architecture of domains, you can always pass null as the domain in the last parameter of a permission
 */
public class Permisions {
    Permission ADMINISTRATOR = new Permission("ADMINISTRATOR", "Can administer the system", AppDomains.BACK_OFFICE, true);
    Permission ASSIGNS_PERMISSIONS = new Permission("ASSIGNS_PERMISSIONS", "Can Assign Permissions to roles", AppDomains.BACK_OFFICE, true);
    Permission MANAGE_USERS = new Permission("MANAGE_USERS", "Can create and edit system users", AppDomains.BACK_OFFICE, true);
    Permission MANAGE_PARTNERS = new Permission("MANAGE_PARTNERS", "Can create, edit, and deactivate partners", AppDomains.BACK_OFFICE, true);
    Permission VIEW_MESSAGES = new Permission("VIEW_MESSAGES", "Can inspect outbound SMS traffic", AppDomains.BACK_OFFICE, true);
    Permission VIEW_AUDIT_LOGS = new Permission("VIEW_AUDIT_LOGS", "Can inspect the admin audit trail", AppDomains.BACK_OFFICE, true);
    Permission VIEW_TRANSACTIONS = new Permission("VIEW_TRANSACTIONS", "Can inspect credit and billing transactions", AppDomains.BACK_OFFICE, true);
    Permission SASULA_INTEGRATION = new Permission("SASULA_INTEGRATION", "Can query customers and post Sasula collections", AppDomains.BACK_OFFICE, true);
    Permission PARTNER_PORTAL = new Permission("PARTNER_PORTAL", "Can access the partner workspace", AppDomains.CLIENT_SIDE, false);
}
