package ru.tinkoff.integration.openbanking.service;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import ru.tinkoff.integration.openbanking.grpc.RandomGrpc;
import ru.tinkoff.integration.openbanking.grpc.Signer;

import java.util.random.RandomGenerator;

public class RandomService extends RandomGrpc.RandomImplBase {

    @Override
    public void getRandom(Signer.GetRandomRequest request,
                          StreamObserver<Signer.GetRandomResponse> response) {
        var size = request.getSize();

        var b = new byte[size];

        RandomGenerator.getDefault().nextBytes(b);

        response.onNext(Signer.GetRandomResponse.newBuilder()
                .setRandom(ByteString.copyFrom(b))
                .build());
        response.onCompleted();
    }
}
