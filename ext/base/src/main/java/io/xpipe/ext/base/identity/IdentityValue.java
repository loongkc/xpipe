package io.xpipe.ext.base.identity;

import io.xpipe.app.storage.DataStoreEntryRef;
import io.xpipe.app.util.EncryptedValue;
import io.xpipe.app.util.SecretRetrievalStrategy;
import io.xpipe.app.util.Validators;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.xpipe.core.util.ValidationException;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = IdentityValue.InPlace.class),
    @JsonSubTypes.Type(value = IdentityValue.Ref.class)
})
public interface IdentityValue {

    static IdentityValue.InPlace of(LocalIdentityStore identityStore) {
        return new InPlace(identityStore);
    }

    static IdentityValue.InPlace none() {
        var s = LocalIdentityStore.builder()
                .password(EncryptedValue.of(new SecretRetrievalStrategy.None()))
                .sshIdentity(EncryptedValue.of(new SshIdentityStrategy.None()))
                .build();
        return of(s);
    }

    static IdentityValue.InPlace of(String user) {
        return of(user, null,null);
    }

    static IdentityValue.InPlace of(String user, SecretRetrievalStrategy password) {
        return of(user, password,null);
    }

    static IdentityValue.InPlace of(String user, SecretRetrievalStrategy password, SshIdentityStrategy sshIdentity) {
        var s = LocalIdentityStore.builder().username(user)
                .password(password != null ? EncryptedValue.of(password) : null)
                .sshIdentity(sshIdentity != null ? EncryptedValue.of(sshIdentity) : null)
                .build();
        return of(s);
    }

    void checkComplete() throws Throwable;

    IdentityStore unwrap();

    boolean isPerUser();

    default void checkCompleteUser() throws ValidationException {
        Validators.nonNull(unwrap().getUsername(), "Identity username");
    }

    default void checkCompletePassword() throws ValidationException {
        Validators.nonNull(unwrap().getPassword(), "Identity password");
        unwrap().getPassword().checkComplete();
    }

    default void checkCompleteSshIdentity() throws ValidationException {
        Validators.nonNull(unwrap().getSshIdentity(), "Identity ssh auth");
        unwrap().getSshIdentity().checkComplete();
    }

    @JsonTypeName("inPlace")
    @Value
    @Jacksonized
    @Builder
    class InPlace implements IdentityValue {

        LocalIdentityStore identityStore;

        @Override
        public void checkComplete() throws Throwable {
            Validators.nonNull(identityStore);
        }

        @Override
        public LocalIdentityStore unwrap() {
            return identityStore;
        }

        @Override
        public boolean isPerUser() {
            return false;
        }
    }

    @JsonTypeName("ref")
    @Value
    @Jacksonized
    @Builder
    class Ref implements IdentityValue {

        DataStoreEntryRef<IdentityStore> ref;

        @Override
        public void checkComplete() throws Throwable {
            Validators.nonNull(ref);
            Validators.isType(ref, IdentityStore.class);
        }

        @Override
        public IdentityStore unwrap() {
            return ref.getStore();
        }

        @Override
        public boolean isPerUser() {
            return ref.get().isPerUserStore();
        }
    }
}
