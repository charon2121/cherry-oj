import { Container, Section } from '@/components/ui/layout';
import { Heading } from '@/components/ui/typography';

function AdminDashboardPage() {
  return (
    <Container className="max-w-none">
      <Section>
        <Heading level={1} className="sr-only">
          Dashboard
        </Heading>
      </Section>
    </Container>
  );
}

export { AdminDashboardPage };
