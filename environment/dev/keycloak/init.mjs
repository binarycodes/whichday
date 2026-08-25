/*
 * Creates the realm Whichday authenticates against, over Keycloak's admin REST API.
 *
 * This rather than a realm export imported with --import-realm: an export is a
 * RealmRepresentation that has to be exactly right or the container refuses to boot,
 * and it tells you so through a Jackson field error rather than anything about
 * Keycloak. Here a wrong field is an HTTP 400 that names it, and everything below is
 * a documented API call.
 *
 * Idempotent, because `./run.sh env up` runs against a Keycloak that may already have
 * all of this. Nothing prompts — there is nobody to answer inside a container. Each
 * step GETs what it is about to create and returns early if it is already there.
 *
 * Every value here is a laptop's. The client secret is in version control and the
 * client will redirect anywhere at all. A deployment configures its own realm — very
 * likely one it already runs for other applications — and shares nothing with this file.
 */

const keycloakUrl = process.env.KC_URL;
const adminUsername = process.env.KC_ADMIN_USERNAME;
const adminPassword = process.env.KC_ADMIN_PASSWORD;

const realmName = 'whichday';
const clientId = 'whichday';
const clientSecret = 'whichday-dev-secret';

/*
 * Two people, because one cannot invite anybody. A poll needs somebody to call it and
 * somebody to answer it, and the invitee search only offers accounts that exist — so a
 * realm with a single user is a realm in which the create flow cannot be finished.
 *
 * The addresses are the ones the design was drawn with (see Sample in the test tree),
 * which is also the point: Whichday identifies a person by their address and nothing
 * else, so these are what the polls are keyed by. Throwing the Keycloak container away
 * therefore costs nothing but the passwords — the same addresses come back to the same
 * polls, which is not true of an application that keys on the provider's subject.
 *
 * No id is set on any of them: the admin API assigns one and ignores a supplied one.
 * Only a realm import can pin an id, and nothing here needs one pinned.
 */
const people = [
    { username: 'ada', password: 'ada', firstName: 'Ada', lastName: 'Lindqvist',
      email: 'ada.lindqvist@acme.com' },
    { username: 'miro', password: 'miro', firstName: 'Miro', lastName: 'Kallio',
      email: 'm.kallio@acme.com' }
];

const realmUrl = `${keycloakUrl}/admin/realms/${realmName}`;

/*
 * Fetched per call rather than once. Creating a realm and then reaching into it is
 * more work than the default token lifetime is long, and a 401 halfway through reads
 * as a permissions problem rather than an expiry.
 */
const authorizationHeader = async () => {
    const response = await fetch(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            username: adminUsername,
            password: adminPassword,
            grant_type: 'password',
            client_id: 'admin-cli'
        })
    });

    if (!response.ok) {
        console.error('Could not authenticate against Keycloak:', await response.text());
        process.exit(1);
    }

    return {
        Authorization: `Bearer ${(await response.json()).access_token}`,
        'Content-Type': 'application/json'
    };
};

const create = async (url, body, describe) => {
    const response = await fetch(url, {
        method: 'POST',
        headers: await authorizationHeader(),
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        console.error(`Failed to create ${describe}:`, await response.text());
        process.exit(1);
    }
    console.log(`Created ${describe}.`);
};

const createRealm = async () => {
    const existing = await fetch(realmUrl, { headers: await authorizationHeader() });
    if (existing.ok) {
        console.log(`Realm '${realmName}' is already there.`);
        return;
    }
    await create(`${keycloakUrl}/admin/realms`, {
        realm: realmName,
        displayName: 'Whichday',
        enabled: true,
        sslRequired: 'none',
        registrationAllowed: false,
        loginWithEmailAllowed: true
    }, `realm '${realmName}'`);
};

const createClient = async () => {
    const existing = await fetch(`${realmUrl}/clients?clientId=${clientId}`, {
        headers: await authorizationHeader()
    });
    const [found] = await existing.json();
    if (found) {
        console.log(`Client '${clientId}' is already there.`);
        return;
    }
    await create(`${realmUrl}/clients`, {
        clientId: clientId,
        name: 'Whichday',
        secret: clientSecret,
        enabled: true,
        protocol: 'openid-connect',
        // Confidential: Whichday holds a secret, so there is no reason to be public.
        publicClient: false,
        standardFlowEnabled: true,
        implicitFlowEnabled: false,
        directAccessGrantsEnabled: false,
        serviceAccountsEnabled: false,
        // Wide open, because a realm's redirect URIs are not Whichday's business to get
        // right — a deployment adds Whichday's exact callback to a realm it already
        // runs, very likely alongside other applications. This one exists so a laptop
        // can log in on whatever port it happens to be using.
        redirectUris: ['*'],
        webOrigins: ['*'],
        attributes: {
            // Without this, signing out lands on a Keycloak error page instead of on
            // Whichday — and the sign-out button is RP-initiated, so it goes there.
            'post.logout.redirect.uris': '*'
        }
    }, `client '${clientId}'`);
};

const createPerson = async (person) => {
    const existing = await fetch(`${realmUrl}/users?username=${person.username}&exact=true`, {
        headers: await authorizationHeader()
    });
    const [found] = await existing.json();
    if (found) {
        console.log(`User '${person.username}' is already there, as ${found.id}.`);
        return;
    }
    await create(`${realmUrl}/users`, {
        username: person.username,
        // A complete profile, deliberately. Keycloak's VERIFY_PROFILE action fires on
        // login for a user missing a required attribute — firstName and lastName are
        // required by the default user profile — and parks the browser on a "complete
        // your account" form that reads like a login which did not take. The two names
        // are also what Whichday shows: it reads the OIDC `name` claim.
        firstName: person.firstName,
        lastName: person.lastName,
        email: person.email,
        // The address is the identity here, so an unverified one is a person who is
        // not who the poll thinks they are. Whichday does not check the claim yet
        // (docs/issues/0010-a-signed-in-address-is-never-verified.md); this realm is
        // set up so that it still works on the day it does.
        emailVerified: true,
        enabled: true,
        requiredActions: [],
        credentials: [{ type: 'password', value: person.password, temporary: false }]
    }, `user '${person.username}' (${person.email})`);
};

await createRealm();
await createClient();
for (const person of people) {
    await createPerson(person);
}

console.log(`Keycloak is ready: realm '${realmName}', client '${clientId}', `
    + `users ${people.map(person => person.username).join(' and ')}.`);
