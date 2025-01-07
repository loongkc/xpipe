package io.xpipe.ext.base.identity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.xpipe.app.util.EncryptedValue;
import io.xpipe.app.util.SecretRetrievalStrategy;
import io.xpipe.app.util.Validators;
import io.xpipe.core.util.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@JsonTypeName("localIdentity")
@Jacksonized
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LocalIdentityStore extends IdentityStore {

    EncryptedValue<SecretRetrievalStrategy> password;
    EncryptedValue<SshIdentityStrategy> sshIdentity;

    @Override
    public SecretRetrievalStrategy getPassword() {
        return password != null ? password.getValue() : null;
    }

    @Override
    public SshIdentityStrategy getSshIdentity() {
        return sshIdentity != null ? sshIdentity.getValue() : null;
    }
}
